package com.yapcore.protocol.via.proxy;

import com.yapcore.protocol.java.ConnState;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.ViaDirection;
import com.yapcore.protocol.via.ViaSession;
import com.yapcore.protocol.via.id.PacketIdDump;
import com.yapcore.protocol.via.transform.LoginSuccessRewriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Paper → client S2C path for the Via proxy.
 */
public final class ViaProxyBackendHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = Logger.getLogger("YaPcore.ViaProxy");

    private final Channel inbound;
    private final ViaProxyHost host;
    private final Queue<ByteBuf> pendingS2C = new ArrayDeque<>();
    private PacketIdDump serverPlayDump;

    public ViaProxyBackendHandler(Channel inbound, ViaProxyHost host) {
        this.inbound = inbound;
        this.host = host;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf packet)) {
            ReferenceCountUtil.release(msg);
            return;
        }
        try {
            ViaSession session = host.session();
            if (session == null || host.transformer() == null) {
                packet.release();
                return;
            }
            packet.markReaderIndex();
            int peekId = -1;
            try {
                peekId = McCodec.readVarInt(packet);
            } catch (Exception ignored) {
                // ignore
            }
            packet.resetReaderIndex();

            session.bumpS2C();
            ByteBuf out = host.transformer().transform(session, ViaDirection.SERVERBOUND_TO_CLIENT, packet);
            packet.release();
            final boolean compressionJustEnabled = session.consumeCompressionPending();
            final int threshold = session.compressionThreshold();
            // Install zlib IMMEDIATELY (before later channelReads). Set Compression itself
            // is written below via frame-enc only (bypasses mc-compress).
            if (compressionJustEnabled) {
                ViaProxyPipeline.installBackendDecompress(ctx.channel().pipeline(), threshold);
                ViaProxyPipeline.installClientCompression(inbound.pipeline(), threshold);
                ViaProxyPipeline.installBackendCompress(ctx.channel().pipeline(), threshold);
                // Flag on now so later S2C in this burst use zlibWrap; SC itself uses
                // writeUncompressedFramed (no zlib header).
                host.setCompressionInstalled(true);
                LOG.info("Via compression enabled threshold=" + threshold
                        + " (manual zlib; Set Compression uncompressed)");
                if (out != null) {
                    ViaProxyPipeline.writeUncompressedFramed(inbound, out).addListener((ChannelFutureListener) f -> {
                        if (f.isSuccess()) {
                            injectLegacyLoginBridge(ctx.channel());
                            drainConfigAutoReplies(ctx.channel());
                        } else {
                            f.channel().close();
                        }
                    });
                } else {
                    injectLegacyLoginBridge(ctx.channel());
                    drainConfigAutoReplies(ctx.channel());
                }
                return;
            }
            // Config resource_pack_push (0x09 on Paper 776): auto-ack only when we do NOT
            // forward (legacy / config-skip). Modern clients must see the prompt and ack
            // themselves or packs never download ("failed to download" / missing textures).
            if (session.state() == ConnState.CONFIG && peekId == 0x09) {
                if (out != null) {
                    if (!shouldForwardResourcePack(session)) {
                        autoAcceptResourcePack(out, false);
                        out.release();
                        out = null;
                    } else {
                        LOG.info("Via config: forwarding resource_pack_push to client");
                    }
                }
            }
            // Play resource_pack_push (id 81 on Paper 776): YaPPacks extras /addResourcePack.
            if (session.state() == ConnState.PLAY && isPlayResourcePackPush(session, peekId)) {
                if (out != null) {
                    if (!shouldForwardResourcePack(session)) {
                        autoAcceptResourcePack(out, true);
                        out.release();
                        out = null;
                    } else {
                        LOG.info("Via play: forwarding remapped resource_pack_push to client");
                    }
                }
            }
            if (out == null) {
                injectLegacyLoginBridge(ctx.channel());
                drainConfigAutoReplies(ctx.channel());
                return;
            }
            ChannelFutureListener afterWrite = f -> {
                if (f.isSuccess()) {
                    injectLegacyLoginBridge(ctx.channel());
                    drainConfigAutoReplies(ctx.channel());
                } else {
                    f.channel().close();
                }
            };
            ViaProxyPipeline.writeFramed(inbound, out, session, host.compressionInstalled())
                    .addListener(afterWrite);
        } catch (Exception e) {
            packet.release();
            LOG.log(Level.WARNING, "Via S2C failed", e);
            ctx.close();
        }
    }

    // flushPendingS2C kept for any residual queue
    private void flushPendingS2C() {
        ViaSession session = host.session();
        ByteBuf next;
        while ((next = pendingS2C.poll()) != null) {
            if (!inbound.isActive()) {
                next.release();
                continue;
            }
            ViaProxyPipeline.writeFramed(inbound, next, session, host.compressionInstalled());
        }
    }

    private void autoAcceptResourcePack(ByteBuf transformedS2C, boolean playPhase) {
        try {
            int mark = transformedS2C.readerIndex();
            McCodec.readVarInt(transformedS2C); // packet id
            byte[] uuid = new byte[16];
            if (transformedS2C.readableBytes() >= 16) {
                transformedS2C.readBytes(uuid);
            }
            transformedS2C.readerIndex(mark);
            // Paper expects ACCEPTED then SUCCESSFULLY_LOADED for forced packs
            host.enqueueC2S(resourcePackStatus(uuid, 3, playPhase), false); // ACCEPTED
            host.enqueueC2S(resourcePackStatus(uuid, 0, playPhase), false); // SUCCESSFULLY_LOADED
            LOG.info("Via " + (playPhase ? "play" : "config")
                    + ": auto-accepted resource pack (accepted+loaded)");
        } catch (Exception e) {
            LOG.log(Level.FINE, "resource pack auto-ack", e);
        }
    }

    /** Paper 776 play S2C resource_pack_push = 81 (also accept dump-resolved ids). */
    private boolean isPlayResourcePackPush(ViaSession session, int serverPacketId) {
        if (serverPacketId == 81) {
            return true;
        }
        try {
            if (serverPlayDump == null) {
                serverPlayDump = PacketIdDump.forProtocol(session.serverProtocol());
            }
            if (serverPlayDump == null) {
                return false;
            }
            String name = serverPlayDump.playS2cName(serverPacketId);
            if (name == null) {
                return false;
            }
            String n = PacketIdDump.canonicalize(name);
            return "resource_pack_push".equals(n) || "add_resource_pack".equals(n);
        } catch (Exception e) {
            return false;
        }
    }

    /** Forward pack prompt only when YaP is actively serving packs (≈1.20.2+ clients). */
    private boolean shouldForwardResourcePack(ViaSession session) {
        if (!host.resourcePackForwardEnabled()) {
            return false;
        }
        if (session.isConfigSkip()) {
            return false;
        }
        if (!session.clientBand().hasConfigurationPhase()) {
            return false;
        }
        // Pre-1.20.2 config either lacks packs or uses incompatible layouts
        return session.clientProtocol() >= 764;
    }

    private static ByteBuf resourcePackStatus(byte[] uuid, int result, boolean playPhase) {
        ByteBuf accept = Unpooled.buffer(24);
        // Config C2S resource_pack = 0x06; Play C2S resource_pack = 49 on Paper 776
        McCodec.writeVarInt(accept, playPhase ? 49 : 0x06);
        accept.writeBytes(uuid);
        McCodec.writeVarInt(accept, result);
        return accept;
    }

    /** After legacy login_success: Login Acknowledged + Client Information toward Paper. */
    private void injectLegacyLoginBridge(Channel backendChannel) {
        ViaSession session = host.session();
        if (session == null || !session.consumePendingLoginAckInject()) {
            return;
        }
        session.setState(ConnState.CONFIG);
        writeToBackend(backendChannel, LoginSuccessRewriter.loginAcknowledged());
        writeToBackend(backendChannel, LoginSuccessRewriter.configClientInformation());
        LOG.info("Via config-skip: injected Login ACK + Client Information for "
                + session.username());
    }

    private void drainConfigAutoReplies(Channel backendChannel) {
        ViaSession session = host.session();
        if (session == null) {
            return;
        }
        ViaSession.ConfigAutoReply reply;
        while ((reply = session.pollConfigAutoReply()) != null) {
            switch (reply) {
                case KNOWN_PACKS -> {
                    writeToBackend(backendChannel, LoginSuccessRewriter.configSelectKnownPacksEmpty());
                    LOG.info("Via config-skip: auto Select Known Packs (empty)");
                }
                case FINISH -> {
                    writeToBackend(backendChannel, LoginSuccessRewriter.configFinishAck());
                    session.clearConfigSkip();
                    session.setState(ConnState.PLAY);
                    LOG.info("Via config-skip: Finish Configuration ACK → PLAY for "
                            + session.username());
                }
            }
        }
    }

    private void writeToBackend(Channel backendChannel, ByteBuf packet) {
        ViaProxyPipeline.writeFramed(backendChannel, packet, host.session(), host.compressionInstalled());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ViaProxyPipeline.closeOnFlush(inbound);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.log(Level.FINE, "via backend", cause);
        ViaProxyPipeline.closeOnFlush(ctx.channel());
        ViaProxyPipeline.closeOnFlush(inbound);
    }
}

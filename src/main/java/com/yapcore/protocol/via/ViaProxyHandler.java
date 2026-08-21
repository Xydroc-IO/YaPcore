package com.yapcore.protocol.via;

import com.yapcore.protocol.compat.ProtocolCompat;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.java.codec.McCompressionCodec;
import com.yapcore.protocol.java.codec.McFrameCodec;
import com.yapcore.protocol.via.transform.LoginSuccessRewriter;
import com.yapcore.protocol.via.transform.PacketTransformer;
import com.yapcore.protocol.java.ConnState;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Public JE edge → Via remap → Paper (or wrapped game) on loopback.
 * Replaces opaque {@link com.yapcore.kernel.JavaKernelProxyHandler} when Via is on.
 */
public final class ViaProxyHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = Logger.getLogger("YaPcore.ViaProxy");

    private final String backendHost;
    private final int backendPort;
    private final int serverProtocol;
    private Channel backend;
    private ViaSession session;
    private PacketTransformer transformer;
    private boolean handshakeDone;
    private boolean compressionInstalled;
    /** True while Set Compression is in-flight to the client — buffer later S2C. */
    private boolean awaitingCompressionFlush;
    private final Queue<ByteBuf> pendingS2C = new ArrayDeque<>();
    /** Serialize C2S writes so login_start never races ahead of handshake on Paper. */
    private boolean c2sWriteInFlight;
    private final Queue<ByteBuf> pendingC2S = new ArrayDeque<>();
    private ChannelHandlerContext inboundCtx;

    public ViaProxyHandler(String backendHost, int backendPort, int serverProtocol) {
        this.backendHost = backendHost;
        this.backendPort = backendPort;
        this.serverProtocol = serverProtocol;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.inboundCtx = ctx;
        Channel inbound = ctx.channel();
        inbound.config().setAutoRead(false);
        Bootstrap boot = new Bootstrap();
        boot.group(inbound.eventLoop())
                .channel(matchingClientChannel(inbound))
                .option(ChannelOption.TCP_NODELAY, true)
                // AUTO_READ true on Paper side so login_success is not stuck unread.
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // frame-dec only — we manually length-prefix C2S writes (avoids
                        // MessageToByteEncoder oddities on Epoll outbound).
                        ch.pipeline()
                                .addLast("frame-dec", new McFrameCodec.Decoder())
                                .addLast("via-backend", new BackendHandler(inbound));
                    }
                });
        boot.connect(backendHost, backendPort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                backend = future.channel();
                inbound.pipeline()
                        .addBefore(ctx.name(), "frame-dec", new McFrameCodec.Decoder());
                // No frame-enc — we manually length-prefix S2C writes
                inbound.config().setAutoRead(true);
                inbound.read();
                backend.read();
            } else {
                LOG.warning("Via backend connect failed: " + future.cause().getMessage());
                inbound.close();
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf packet)) {
            ReferenceCountUtil.release(msg);
            return;
        }
        try {
            if (!handshakeDone) {
                if (!captureHandshake(packet)) {
                    packet.release();
                    ctx.close();
                    return;
                }
                handshakeDone = true;
            }
            if (session == null || transformer == null || backend == null || !backend.isActive()) {
                LOG.warning("Via C2S dropped — backend not ready yet");
                packet.release();
                return;
            }
            session.bumpC2S();
            ByteBuf out = transformer.transform(session, ViaDirection.CLIENTBOUND_TO_SERVER, packet);
            packet.release();
            if (out == null) {
                return;
            }
            enqueueC2S(out);
        } catch (Exception e) {
            packet.release();
            LOG.log(Level.WARNING, "Via C2S failed", e);
            ctx.close();
        }
    }

    private void enqueueC2S(ByteBuf out) {
        if (c2sWriteInFlight) {
            pendingC2S.add(out);
            return;
        }
        writeC2S(out);
    }

    private void writeC2S(ByteBuf out) {
        if (backend == null || !backend.isActive()) {
            out.release();
            return;
        }
        c2sWriteInFlight = true;
        if (out.readableBytes() > 9 || (out.getByte(out.readerIndex()) & 0xff) != 0x04) {
            LOG.info("Via C2S → Paper bytes=" + out.readableBytes()
                    + " hex=" + hexPrefix(out, 24));
        }
        backendWrite(backend, out).addListener((ChannelFutureListener) f -> {
            c2sWriteInFlight = false;
            if (!f.isSuccess()) {
                LOG.warning("Via C2S write to Paper failed: " + f.cause());
                f.channel().close();
                return;
            }
            ByteBuf next = pendingC2S.poll();
            if (next != null) {
                writeC2S(next);
            }
        });
    }

    private static String hexPrefix(ByteBuf buf, int max) {
        int n = Math.min(max, buf.readableBytes());
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02x", buf.getByte(buf.readerIndex() + i) & 0xff));
        }
        return sb.toString();
    }

    /** Set Compression — raw length frame, never zlib-wrapped. */
    private io.netty.channel.ChannelFuture writeUncompressedFramed(Channel ch, ByteBuf packet) {
        ByteBuf framed = ch.alloc().buffer(packet.readableBytes() + 5);
        McCodec.writeVarInt(framed, packet.readableBytes());
        framed.writeBytes(packet, packet.readerIndex(), packet.readableBytes());
        packet.release();
        return ch.writeAndFlush(framed);
    }

    /**
     * S2C/C2S to a peer: length frame, and after Set Compression add Minecraft zlib header.
     * Done manually — pipeline MessageToMessageEncoder+frame-enc was corrupting frames.
     */
    private io.netty.channel.ChannelFuture writeFramed(Channel ch, ByteBuf packet) {
        int threshold = session != null ? session.compressionThreshold() : -1;
        ByteBuf payload = packet;
        if (compressionInstalled && threshold >= 0) {
            payload = zlibWrap(ch, packet, threshold);
        }
        ByteBuf framed = ch.alloc().buffer(payload.readableBytes() + 5);
        McCodec.writeVarInt(framed, payload.readableBytes());
        framed.writeBytes(payload, payload.readerIndex(), payload.readableBytes());
        payload.release();
        return ch.writeAndFlush(framed);
    }

    /** Minecraft post-compression body: VarInt(dataLength) + data (0 = uncompressed). */
    private static ByteBuf zlibWrap(Channel ch, ByteBuf packet, int threshold) {
        int readable = packet.readableBytes();
        if (readable < threshold) {
            ByteBuf out = ch.alloc().buffer(readable + 5);
            McCodec.writeVarInt(out, 0);
            out.writeBytes(packet, packet.readerIndex(), readable);
            packet.release();
            return out;
        }
        byte[] input = new byte[readable];
        packet.getBytes(packet.readerIndex(), input);
        packet.release();
        java.util.zip.Deflater deflater = new java.util.zip.Deflater();
        try {
            deflater.setInput(input);
            deflater.finish();
            byte[] buf = new byte[Math.max(64, readable / 2)];
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(readable);
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                if (n > 0) {
                    baos.write(buf, 0, n);
                } else {
                    break;
                }
            }
            byte[] compressed = baos.toByteArray();
            ByteBuf out = ch.alloc().buffer(compressed.length + 5);
            McCodec.writeVarInt(out, readable);
            out.writeBytes(compressed);
            return out;
        } finally {
            deflater.end();
        }
    }

    private static void ensureFrameEnc(ChannelPipeline pipeline, String after) {
        // Client path uses manual framing; only ensure backend has frame-enc when using pipeline compress.
        if (pipeline.get("frame-enc") == null && pipeline.get("mc-compress") != null) {
            pipeline.addAfter(after, "frame-enc", new McFrameCodec.Encoder());
        }
    }

    private io.netty.channel.ChannelFuture backendWrite(Channel backend, ByteBuf packet) {
        return writeFramed(backend, packet);
    }

    private boolean captureHandshake(ByteBuf packet) {
        packet.markReaderIndex();
        try {
            int id = McCodec.readVarInt(packet);
            if (id != 0x00) {
                return false;
            }
            int clientProto = McCodec.readVarInt(packet);
            session = new ViaSession(clientProto, serverProtocol, backendPort);
            transformer = new PacketTransformer(session);
            ProtocolCompat.onJavaJoin("via-pending", clientProto);
            packet.resetReaderIndex();
            return true;
        } catch (Exception e) {
            packet.resetReaderIndex();
            return false;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (backend != null) {
            closeOnFlush(backend);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.log(Level.FINE, "via inbound", cause);
        closeOnFlush(ctx.channel());
        if (backend != null) {
            closeOnFlush(backend);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Channel> matchingClientChannel(Channel inbound) {
        String name = inbound.getClass().getName();
        try {
            if (name.contains("epoll")) {
                return (Class<? extends Channel>) Class.forName(
                        "io.netty.channel.epoll.EpollSocketChannel");
            }
            if (name.contains("kqueue")) {
                return (Class<? extends Channel>) Class.forName(
                        "io.netty.channel.kqueue.KQueueSocketChannel");
            }
        } catch (ClassNotFoundException e) {
            LOG.warning("Native channel missing, NIO fallback: " + e.getMessage());
        }
        return NioSocketChannel.class;
    }

    private static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            // Do not write EMPTY_BUFFER — McFrameCodec would emit a 0-length frame
            // and Paper throws CorruptedFrameException: Frame length cannot be zero.
            ch.close();
        }
    }

    private final class BackendHandler extends ChannelInboundHandlerAdapter {
        private final Channel inbound;

        BackendHandler(Channel inbound) {
            this.inbound = inbound;
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
                if (session == null || transformer == null) {
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
                ByteBuf out = transformer.transform(session, ViaDirection.SERVERBOUND_TO_CLIENT, packet);
                packet.release();
                final boolean compressionJustEnabled = session.consumeCompressionPending();
                final int threshold = session.compressionThreshold();
                // Install zlib IMMEDIATELY (before later channelReads). Set Compression itself
                // is written below via frame-enc only (bypasses mc-compress).
                if (compressionJustEnabled) {
                    installBackendDecompress(ctx.channel().pipeline(), threshold);
                    installClientCompression(inbound.pipeline(), threshold);
                    installBackendCompress(ctx.channel().pipeline(), threshold);
                    // Flag on now so later S2C in this burst use zlibWrap; SC itself uses
                    // writeUncompressedFramed (no zlib header).
                    compressionInstalled = true;
                    LOG.info("Via compression enabled threshold=" + threshold
                            + " (manual zlib; Set Compression uncompressed)");
                    if (out != null) {
                        writeUncompressedFramed(inbound, out).addListener((ChannelFutureListener) f -> {
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
                // Config resource_pack_push (0x09 on Paper 776): always auto-ack toward Paper
                // so the config FSM never stalls; forward remapped pack only to clients that
                // speak config resource-pack packets (1.20.2+).
                if (session.state() == ConnState.CONFIG && peekId == 0x09) {
                    if (out != null) {
                        autoAcceptResourcePack(out, false);
                        if (!shouldForwardResourcePack(session)) {
                            out.release();
                            out = null;
                        }
                    }
                }
                // Play resource_pack_push (id 81 on Paper 776): YaPPacks extras /addResourcePack.
                // Same auto-ack + optional forward — wrong/missing play ID remaps caused resets.
                if (session.state() == ConnState.PLAY && isPlayResourcePackPush(peekId)) {
                    if (out != null) {
                        autoAcceptResourcePack(out, true);
                        if (!shouldForwardResourcePack(session)) {
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
                writeFramed(inbound, out).addListener(afterWrite);
            } catch (Exception e) {
                packet.release();
                LOG.log(Level.WARNING, "Via S2C failed", e);
                ctx.close();
            }
        }

        // flushPendingS2C kept for any residual queue
        private void flushPendingS2C() {
            ByteBuf next;
            while ((next = pendingS2C.poll()) != null) {
                if (!inbound.isActive()) {
                    next.release();
                    continue;
                }
                writeFramed(inbound, next);
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
                enqueueC2S(resourcePackStatus(uuid, 3, playPhase)); // ACCEPTED
                enqueueC2S(resourcePackStatus(uuid, 0, playPhase)); // SUCCESSFULLY_LOADED
                LOG.info("Via " + (playPhase ? "play" : "config")
                        + ": auto-accepted resource pack (accepted+loaded)");
            } catch (Exception e) {
                LOG.log(Level.FINE, "resource pack auto-ack", e);
            }
        }

        /** Paper 776 play S2C resource_pack_push = 81 (also accept dump-resolved ids). */
        private boolean isPlayResourcePackPush(int serverPacketId) {
            if (serverPacketId == 81) {
                return true;
            }
            try {
                var dump = com.yapcore.protocol.via.id.PacketIdDump.forProtocol(
                        session.serverProtocol());
                String name = dump.playS2cName(serverPacketId);
                if (name == null) {
                    return false;
                }
                String n = com.yapcore.protocol.via.id.PacketIdDump.canonicalize(name);
                return "resource_pack_push".equals(n) || "add_resource_pack".equals(n);
            } catch (Exception e) {
                return false;
            }
        }

        /** Forward pack prompt to JE clients that have config/play add_resource_pack (≈1.20.2+). */
        private static boolean shouldForwardResourcePack(ViaSession session) {
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
            // Prefer framed write (compression-aware)
            writeFramed(backendChannel, packet);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            closeOnFlush(inbound);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.log(Level.FINE, "via backend", cause);
            closeOnFlush(ctx.channel());
            closeOnFlush(inbound);
        }
    }

    private static io.netty.channel.ChannelFuture writeFromViaProxy(Channel inbound, ByteBuf out) {
        ChannelHandlerContext viaCtx = inbound.pipeline().context("via-proxy");
        if (viaCtx != null) {
            return viaCtx.writeAndFlush(out);
        }
        return inbound.writeAndFlush(out);
    }

    private static void installBackendDecompress(ChannelPipeline pipeline, int threshold) {
        if (pipeline.get("mc-decompress") != null) {
            ((McCompressionCodec.Decoder) pipeline.get("mc-decompress")).setThreshold(threshold);
            return;
        }
        McCompressionCodec.Decoder decoder = new McCompressionCodec.Decoder();
        decoder.setThreshold(threshold);
        pipeline.addAfter("frame-dec", "mc-decompress", decoder);
    }

    private static void installBackendCompress(ChannelPipeline pipeline, int threshold) {
        // Outbound C2S uses writeFramed() manual zlib — do NOT also install a pipeline
        // Encoder (double-wrap → Paper "unknown packet id" / client partial packet 256).
        if (pipeline.get("mc-compress") != null) {
            pipeline.remove("mc-compress");
        }
        if (pipeline.get("frame-enc") != null) {
            pipeline.remove("frame-enc");
        }
    }

    private static void installClientCompression(ChannelPipeline pipeline, int threshold) {
        // Inbound only: decompress client→Via. S2C zlib is applied in writeFramed().
        if (pipeline.get("mc-decompress") == null) {
            McCompressionCodec.Decoder decoder = new McCompressionCodec.Decoder();
            decoder.setThreshold(threshold);
            pipeline.addAfter("frame-dec", "mc-decompress", decoder);
        } else {
            ((McCompressionCodec.Decoder) pipeline.get("mc-decompress")).setThreshold(threshold);
        }
        // Remove any leftover outbound compress/frame-enc from older builds
        if (pipeline.get("mc-compress") != null) {
            pipeline.remove("mc-compress");
        }
        if (pipeline.get("frame-enc") != null) {
            pipeline.remove("frame-enc");
        }
    }
}

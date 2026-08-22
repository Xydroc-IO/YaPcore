package com.yapcore.protocol.via;

import com.yapcore.protocol.java.ConnState;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.java.codec.McFrameCodec;
import com.yapcore.protocol.via.proxy.ViaProxyBackendHandler;
import com.yapcore.protocol.via.proxy.ViaProxyHandshake;
import com.yapcore.protocol.via.proxy.ViaProxyHost;
import com.yapcore.protocol.via.proxy.ViaProxyPipeline;
import com.yapcore.protocol.via.transform.PacketTransformer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Public JE edge → Via remap → Paper (or wrapped game) on loopback.
 * Replaces opaque {@link com.yapcore.kernel.JavaKernelProxyHandler} when Via is on.
 */
public final class ViaProxyHandler extends ChannelInboundHandlerAdapter implements ViaProxyHost {

    private static final Logger LOG = Logger.getLogger("YaPcore.ViaProxy");

    private final String backendHost;
    private final int backendPort;
    private final int serverProtocol;
    private Channel backend;
    private ViaSession session;
    private PacketTransformer transformer;
    private boolean handshakeDone;
    private boolean compressionInstalled;
    /** Serialize C2S writes so login_start never races ahead of handshake on Paper. */
    private boolean c2sWriteInFlight;
    private final ArrayDeque<ByteBuf> pendingC2S = new ArrayDeque<>();
    private ChannelHandlerContext inboundCtx;

    public ViaProxyHandler(String backendHost, int backendPort, int serverProtocol) {
        this.backendHost = backendHost;
        this.backendPort = backendPort;
        this.serverProtocol = serverProtocol;
    }

    @Override
    public ViaSession session() {
        return session;
    }

    @Override
    public PacketTransformer transformer() {
        return transformer;
    }

    @Override
    public boolean compressionInstalled() {
        return compressionInstalled;
    }

    @Override
    public void setCompressionInstalled(boolean installed) {
        this.compressionInstalled = installed;
    }

    @Override
    public ChannelHandlerContext inboundCtx() {
        return inboundCtx;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.inboundCtx = ctx;
        Channel inbound = ctx.channel();
        inbound.config().setAutoRead(false);
        Bootstrap boot = new Bootstrap();
        boot.group(inbound.eventLoop())
                .channel(ViaProxyPipeline.matchingClientChannel(inbound))
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
                                .addLast("via-backend", new ViaProxyBackendHandler(inbound, ViaProxyHandler.this));
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
                ViaProxyHandshake.Result handshake = ViaProxyHandshake.capture(
                        packet, serverProtocol, backendPort);
                if (handshake == null) {
                    packet.release();
                    ctx.close();
                    return;
                }
                session = handshake.session();
                transformer = handshake.transformer();
                handshakeDone = true;
            }
            if (session == null || transformer == null || backend == null || !backend.isActive()) {
                // WARN storm on 2 workers after Paper kicks stalls survivors — FINE only.
                LOG.fine("Via C2S dropped — backend not ready yet");
                packet.release();
                if (backend != null && !backend.isActive()) {
                    ctx.close();
                }
                return;
            }
            boolean keepAlive = isPlayKeepAliveC2S(packet);
            session.bumpC2S();
            ByteBuf out = transformer.transform(session, ViaDirection.CLIENTBOUND_TO_SERVER, packet);
            packet.release();
            if (out == null) {
                return;
            }
            enqueueC2S(out, keepAlive);
        } catch (Exception e) {
            packet.release();
            LOG.log(Level.WARNING, "Via C2S failed", e);
            ctx.close();
        }
    }

    /** Peek play keep_alive before transform — must not lose to position FIFO under load. */
    private boolean isPlayKeepAliveC2S(ByteBuf packet) {
        if (session == null || session.state() != ConnState.PLAY) {
            return false;
        }
        int mark = packet.readerIndex();
        try {
            int id = McCodec.readVarInt(packet);
            return id == session.clientBand().keepAliveSbId();
        } catch (Exception e) {
            return false;
        } finally {
            packet.readerIndex(mark);
        }
    }

    @Override
    public void enqueueC2S(ByteBuf out, boolean keepAlive) {
        if (c2sWriteInFlight) {
            if (keepAlive) {
                pendingC2S.addFirst(out);
            } else {
                pendingC2S.addLast(out);
            }
            return;
        }
        writeC2S(out);
    }

    private void writeC2S(ByteBuf out) {
        if (backend == null || !backend.isActive()) {
            out.release();
            if (inboundCtx != null) {
                inboundCtx.close();
            }
            return;
        }
        c2sWriteInFlight = true;
        // Never INFO-log per-packet C2S: under active bot physics this stalls the Netty
        // event loop → client Timed outs while Paper MSPT stays fine (active150 148→62).
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Via C2S → Paper bytes=" + out.readableBytes()
                    + " hex=" + ViaProxyPipeline.hexPrefix(out, 24));
        }
        ViaProxyPipeline.writeFramed(backend, out, session, compressionInstalled)
                .addListener((ChannelFutureListener) f -> {
            c2sWriteInFlight = false;
            if (!f.isSuccess()) {
                LOG.fine("Via C2S write to Paper failed: " + f.cause());
                f.channel().close();
                if (inboundCtx != null) {
                    inboundCtx.close();
                }
                return;
            }
            ByteBuf next = pendingC2S.poll();
            if (next != null) {
                writeC2S(next);
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (backend != null) {
            ViaProxyPipeline.closeOnFlush(backend);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.log(Level.FINE, "via inbound", cause);
        ViaProxyPipeline.closeOnFlush(ctx.channel());
        if (backend != null) {
            ViaProxyPipeline.closeOnFlush(backend);
        }
    }
}

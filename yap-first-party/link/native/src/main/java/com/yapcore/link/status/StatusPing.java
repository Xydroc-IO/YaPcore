package com.yapcore.link.status;

import com.yapcore.link.LinkConfig;
import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.protocol.McFrameCodec;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** TCP status ping against a backend (handshake status + request). */
public final class StatusPing {

    private static final Logger LOG = Logger.getLogger("YaP.Link.StatusPing");

    private StatusPing() {
    }

    public static ServerStatus pingBlocking(LinkConfig.Backend backend, int timeoutMs) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Promise<ServerStatus> promise = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast("frame-dec", new McFrameCodec.Decoder())
                                    .addLast("frame-enc", new McFrameCodec.Encoder())
                                    .addLast("ping", new PingHandler(backend, promise));
                        }
                    });
            ChannelFuture connect = b.connect(backend.host(), backend.port());
            if (!connect.awaitUninterruptibly(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new java.net.SocketTimeoutException("connect timeout " + backend.name());
            }
            if (!connect.isSuccess()) {
                Throwable cause = connect.cause();
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw new Exception(cause);
            }
            Channel ch = connect.channel();
            if (!promise.awaitUninterruptibly(timeoutMs, TimeUnit.MILLISECONDS)) {
                ch.close();
                throw new java.net.SocketTimeoutException("status timeout " + backend.name());
            }
            if (!promise.isSuccess()) {
                throw new Exception(promise.cause());
            }
            return promise.getNow();
        } finally {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(3, TimeUnit.SECONDS);
        }
    }

    private static final class PingHandler extends ChannelInboundHandlerAdapter {
        private final LinkConfig.Backend backend;
        private final Promise<ServerStatus> promise;

        PingHandler(LinkConfig.Backend backend, Promise<ServerStatus> promise) {
            this.backend = backend;
            this.promise = promise;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ByteBuf hs = Unpooled.buffer();
            McCodec.writeVarInt(hs, 0x00);
            McCodec.writeVarInt(hs, 776);
            McCodec.writeString(hs, backend.host());
            hs.writeShort(backend.port());
            McCodec.writeVarInt(hs, 1);
            ctx.writeAndFlush(hs).addListener(f -> {
                if (f.isSuccess()) {
                    ByteBuf req = Unpooled.buffer();
                    McCodec.writeVarInt(req, 0x00);
                    ctx.writeAndFlush(req);
                } else {
                    promise.tryFailure(f.cause());
                }
            });
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) {
                return;
            }
            try {
                int packetId = McCodec.readVarInt(buf);
                if (packetId == 0x00) {
                    String json = McCodec.readString(buf, 65536);
                    if (!promise.trySuccess(ServerStatus.parseJson(json))) {
                        LOG.fine("status promise already done for " + backend.name());
                    }
                }
            } catch (Exception e) {
                promise.tryFailure(e);
            } finally {
                buf.release();
                ctx.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            promise.tryFailure(cause);
            ctx.close();
        }
    }
}

package com.yapcore.kernel;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transparent TCP byte relay: public JE client ↔ Mojang game kernel on loopback.
 * Channel type must match the EventLoopGroup (Epoll↔Epoll, NIO↔NIO).
 */
public final class JavaKernelProxyHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = Logger.getLogger("YaPcore.KernelProxy");

    private final String kernelHost;
    private final int kernelPort;
    private Channel backend;

    public JavaKernelProxyHandler(String kernelHost, int kernelPort, EventLoopGroup ignoredWorker) {
        this.kernelHost = kernelHost;
        this.kernelPort = kernelPort;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Channel inbound = ctx.channel();
        Class<? extends Channel> clientChannel = matchingClientChannel(inbound);
        Bootstrap boot = new Bootstrap();
        // Same event loop as inbound — required for Epoll/KQueue compatibility
        boot.group(inbound.eventLoop())
                .channel(clientChannel)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.AUTO_READ, false)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new BackendHandler(inbound));
                    }
                });
        boot.connect(kernelHost, kernelPort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                backend = future.channel();
                inbound.read();
            } else {
                LOG.warning("Kernel proxy connect failed: " + future.cause().getMessage());
                inbound.close();
            }
        });
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
            LOG.warning("Native client channel missing, falling back to NIO: " + e.getMessage());
        }
        return NioSocketChannel.class;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (backend != null && backend.isActive()) {
            backend.writeAndFlush(msg).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    ctx.channel().read();
                } else {
                    f.channel().close();
                }
            });
        } else {
            ReferenceCountUtil.release(msg);
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
        LOG.log(Level.FINE, "proxy inbound", cause);
        closeOnFlush(ctx.channel());
        if (backend != null) {
            closeOnFlush(backend);
        }
    }

    private static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            // Do not write EMPTY_BUFFER — framed pipelines emit VarInt(0) and
            // vanilla clients throw CorruptedFrameException: Frame length cannot be zero.
            ch.close();
        }
    }

    private static final class BackendHandler extends ChannelInboundHandlerAdapter {
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
            inbound.writeAndFlush(msg).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    ctx.channel().read();
                } else {
                    f.channel().close();
                }
            });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            closeOnFlush(inbound);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.log(Level.FINE, "proxy backend", cause);
            closeOnFlush(ctx.channel());
            closeOnFlush(inbound);
        }
    }
}

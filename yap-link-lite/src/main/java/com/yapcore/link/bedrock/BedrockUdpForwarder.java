package com.yapcore.link.bedrock;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Public Bedrock UDP edge with per-client sockets to the Geyser/chassis backend.
 */
public final class BedrockUdpForwarder {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private final InetSocketAddress bind;
    private final InetSocketAddress backend;
    private EventLoopGroup group;
    private Channel listen;
    private final Map<InetSocketAddress, Channel> sessions = new ConcurrentHashMap<>();

    public BedrockUdpForwarder(String bindHost, int bindPort, String backendHost, int backendPort) {
        this.bind = new InetSocketAddress(bindHost.isBlank() ? "0.0.0.0" : bindHost, bindPort);
        this.backend = new InetSocketAddress(backendHost, backendPort);
    }

    public synchronized void start() throws InterruptedException {
        if (listen != null) {
            return;
        }
        group = new NioEventLoopGroup(2);
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ch.pipeline().addLast(new ListenHandler());
                    }
                });
        listen = b.bind(bind).sync().channel();
        LOG.info("Bedrock UDP edge " + bind + " → Geyser/chassis " + backend
                + " (translation on backend)");
    }

    public synchronized void stop() {
        for (Channel ch : sessions.values()) {
            ch.close();
        }
        sessions.clear();
        if (listen != null) {
            listen.close().syncUninterruptibly();
            listen = null;
        }
        if (group != null) {
            group.shutdownGracefully();
            group = null;
        }
    }

    private Channel sessionFor(InetSocketAddress client) throws InterruptedException {
        Channel existing = sessions.get(client);
        if (existing != null && existing.isActive()) {
            return existing;
        }
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ch.pipeline().addLast(new BackendHandler(client));
                    }
                });
        Channel ch = b.bind(0).sync().channel();
        sessions.put(client, ch);
        ch.closeFuture().addListener(f -> sessions.remove(client, ch));
        return ch;
    }

    private final class ListenHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            InetSocketAddress client = msg.sender();
            Channel session = sessionFor(client);
            session.writeAndFlush(new DatagramPacket(msg.content().retain(), backend));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.log(Level.FINE, "bedrock listen", cause);
        }
    }

    private final class BackendHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final InetSocketAddress client;

        BackendHandler(InetSocketAddress client) {
            this.client = client;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
            if (listen != null && listen.isActive()) {
                listen.writeAndFlush(new DatagramPacket(msg.content().retain(), client));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.log(Level.FINE, "bedrock session " + client, cause);
            ctx.close();
        }
    }
}

package com.yapcore.link.bedrock;

import com.yapcore.link.LinkConfig;

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
 * Bedrock UDP edge: routes each client to a per-backend Geyser/chassis target.
 * Default backend from {@code bedrock-backend}; override with {@code servers.<name>.bedrock=host:port}.
 */
public final class BedrockUdpForwarder {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private final LinkConfig config;
    private final InetSocketAddress bind;
    private EventLoopGroup group;
    private Channel listen;
    private final Map<InetSocketAddress, Session> sessions = new ConcurrentHashMap<>();

    public BedrockUdpForwarder(LinkConfig config) {
        this.config = config;
        this.bind = new InetSocketAddress(config.bedrockBindHost(), config.bedrockBindPort());
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
        LOG.info("Bedrock UDP edge " + bind + " — per-backend routing enabled");
    }

    public synchronized void stop() {
        for (Session s : sessions.values()) {
            s.close();
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

    private InetSocketAddress resolveBackend() {
        LinkConfig.Backend def = config.resolveTry();
        String host = config.bedrockBackendFor(def.name()).host();
        int port = config.bedrockBackendFor(def.name()).port();
        return new InetSocketAddress(host, port);
    }

    private Session sessionFor(InetSocketAddress client) throws InterruptedException {
        Session existing = sessions.get(client);
        if (existing != null && existing.isActive()) {
            return existing;
        }
        InetSocketAddress backend = resolveBackend();
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
        Session session = new Session(ch, backend);
        sessions.put(client, session);
        ch.closeFuture().addListener(f -> sessions.remove(client, session));
        LOG.fine("Bedrock session " + client + " → " + backend);
        return session;
    }

    /** Route a specific client to a named backend's bedrock target. */
    public void routeClient(InetSocketAddress client, String serverName) {
        LinkConfig.BedrockTarget target = config.bedrockBackendFor(serverName);
        Session existing = sessions.get(client);
        if (existing != null) {
            existing.setBackend(new InetSocketAddress(target.host(), target.port()));
        }
    }

    private final class ListenHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
            InetSocketAddress client = msg.sender();
            Session session = sessionFor(client);
            session.forward(msg.content().retain());
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

    private final class Session {
        private final Channel channel;
        private volatile InetSocketAddress backend;

        Session(Channel channel, InetSocketAddress backend) {
            this.channel = channel;
            this.backend = backend;
        }

        boolean isActive() {
            return channel.isActive();
        }

        void setBackend(InetSocketAddress backend) {
            this.backend = backend;
        }

        void forward(io.netty.buffer.ByteBuf data) {
            if (channel.isActive()) {
                channel.writeAndFlush(new DatagramPacket(data, backend));
            } else {
                data.release();
            }
        }

        void close() {
            channel.close();
        }
    }
}

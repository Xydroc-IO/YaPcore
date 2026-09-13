package com.yapcore.link.bedrock;

import com.yapcore.link.LinkConfig;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bedrock UDP edge: routes each client to a per-backend Geyser/chassis target.
 * Default backend from {@code bedrock-backend}; override with {@code servers.<name>.bedrock=host:port}.
 * Supports comma-separated {@code bedrock-bind} (e.g. {@code 0.0.0.0:25565,0.0.0.0:19132}) so
 * shared-port join and Bedrock's default :19132 both work.
 *
 * <p>Unconnected Ping is answered <em>locally</em> (Geyser pattern) so list discovery does not
 * depend on chassis latency, session churn, or backend GUID/port rewrite mistakes.
 */
public final class BedrockUdpForwarder {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private final LinkConfig config;
    private final List<InetSocketAddress> binds;
    private final long serverGuid;
    private final IntSupplier onlineCount;
    /** Reads edge UDP; must never block on {@code bind().sync()}. */
    private EventLoopGroup listenGroup;
    /** Outbound per-client UDP; separate so session bind cannot deadlock the listen loop. */
    private EventLoopGroup sessionGroup;
    private final List<Channel> listens = new ArrayList<>();
    private final Map<InetSocketAddress, Session> sessions = new ConcurrentHashMap<>();
    /**
     * Per-client INFO budget (not a global 64). A global cap burned out on LAN
     * scanners and made real phone joins look like "server sees nothing".
     * Always log the first packet and any large OCR-sized datagram per client.
     */
    private final Map<String, java.util.concurrent.atomic.AtomicInteger> edgePktLogsLeft =
            new ConcurrentHashMap<>();
    private static final int EDGE_PKT_LOGS_PER_CLIENT = 8;
    private static final int EDGE_OCR_MIN_BYTES = 200;

    public BedrockUdpForwarder(LinkConfig config) {
        this(config, () -> 0);
    }

    public BedrockUdpForwarder(LinkConfig config, IntSupplier onlineCount) {
        this.config = config;
        this.binds = config.bedrockBindAddresses();
        this.serverGuid = ThreadLocalRandom.current().nextLong();
        this.onlineCount = onlineCount != null ? onlineCount : () -> 0;
    }

    public synchronized void start() throws InterruptedException {
        if (!listens.isEmpty()) {
            return;
        }
        // Two groups: ListenHandler runs on listenGroup and used to call bind(0).sync()
        // on the same group → classic Netty deadlock → Recv-Q climbs, no RakNet pong.
        listenGroup = new NioEventLoopGroup(2);
        sessionGroup = new NioEventLoopGroup(2);
        Exception firstFailure = null;
        for (InetSocketAddress bind : binds) {
            try {
                Bootstrap b = new Bootstrap();
                b.group(listenGroup)
                        .channel(NioDatagramChannel.class)
                        .option(ChannelOption.SO_BROADCAST, true)
                        .option(ChannelOption.SO_REUSEADDR, true)
                        .handler(new ChannelInitializer<NioDatagramChannel>() {
                            @Override
                            protected void initChannel(NioDatagramChannel ch) {
                                ch.pipeline().addLast(new ListenHandler());
                            }
                        });
                Channel ch = b.bind(bind).sync().channel();
                listens.add(ch);
                LOG.info("Bedrock UDP edge " + bind + " — per-backend routing enabled");
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Bedrock UDP bind failed on " + bind + ": " + e.getMessage(), e);
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (listens.isEmpty()) {
            shutdownGroups();
            if (firstFailure instanceof InterruptedException ie) {
                throw ie;
            }
            if (firstFailure instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Bedrock UDP bind failed for all addresses: " + binds,
                    firstFailure);
        }
        if (firstFailure != null) {
            LOG.warning("Bedrock UDP partially bound (" + listens.size() + "/" + binds.size()
                    + "); first failure: " + firstFailure.getMessage());
        }
    }

    public synchronized void stop() {
        for (Session s : sessions.values()) {
            s.close();
        }
        sessions.clear();
        for (Channel listen : listens) {
            listen.close().syncUninterruptibly();
        }
        listens.clear();
        shutdownGroups();
    }

    private void shutdownGroups() {
        if (listenGroup != null) {
            listenGroup.shutdownGracefully();
            listenGroup = null;
        }
        if (sessionGroup != null) {
            sessionGroup.shutdownGracefully();
            sessionGroup = null;
        }
    }

    private InetSocketAddress resolveBackend() {
        LinkConfig.Backend def = config.resolveTry();
        String host = config.bedrockBackendFor(def.name()).host();
        int port = config.bedrockBackendFor(def.name()).port();
        return new InetSocketAddress(host, port);
    }

    private Session sessionFor(InetSocketAddress client, Channel edge) throws InterruptedException {
        Session existing = sessions.get(client);
        if (existing != null && existing.isActive()) {
            existing.setEdge(edge);
            return existing;
        }
        InetSocketAddress backend = resolveBackend();
        Bootstrap b = new Bootstrap();
        // sessionGroup ≠ listenGroup: bind().sync() from ListenHandler is safe.
        b.group(sessionGroup)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ch.pipeline().addLast(new BackendHandler(client));
                    }
                });
        Channel ch = b.bind(0).sync().channel();
        Session session = new Session(ch, backend, edge);
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
            ByteBuf content = msg.content();

            // List discovery: answer here (do not open a backend session per scanner ping).
            if (RakNetUnconnected.isUnconnectedPing(content)) {
                replyUnconnectedPong(ctx, client, content);
                return;
            }

            Session session = sessionFor(client, ctx.channel());
            int bytes = content.readableBytes();
            String clientKey = client.getAddress().getHostAddress();
            java.util.concurrent.atomic.AtomicInteger left = edgePktLogsLeft.computeIfAbsent(
                    clientKey, k -> new java.util.concurrent.atomic.AtomicInteger(EDGE_PKT_LOGS_PER_CLIENT));
            // Always surface join-sized packets; throttle tiny datagram spam per IP.
            if (bytes >= EDGE_OCR_MIN_BYTES || left.getAndDecrement() > 0) {
                LOG.info("BE edge pkt from=" + client + " bytes=" + bytes + " → " + session.backend());
            }
            session.forward(content.retain());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.log(Level.FINE, "bedrock listen", cause);
        }
    }

    private void replyUnconnectedPong(ChannelHandlerContext ctx,
                                      InetSocketAddress client,
                                      ByteBuf ping) {
        long pingTime;
        try {
            pingTime = RakNetUnconnected.readPingTime(ping);
        } catch (Exception ignored) {
            pingTime = System.currentTimeMillis();
        }
        int edgePort = edgePort(ctx.channel());
        String motd = RakNetUnconnected.buildMotd(
                config.motd(),
                config.bedrockMotdProtocol(),
                config.bedrockMotdVersion(),
                Math.max(0, onlineCount.getAsInt()),
                config.maxPlayers(),
                serverGuid,
                config.bedrockMotdSub(),
                edgePort,
                edgePort);
        ByteBuf pong = RakNetUnconnected.buildPong(pingTime, serverGuid, motd);
        ctx.writeAndFlush(new DatagramPacket(pong, client));
    }

    private static int edgePort(Channel channel) {
        if (channel != null && channel.localAddress() instanceof InetSocketAddress local) {
            return local.getPort();
        }
        return 19132;
    }

    private final class BackendHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final InetSocketAddress client;

        BackendHandler(InetSocketAddress client) {
            this.client = client;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
            Session session = sessions.get(client);
            Channel edge = session != null ? session.edge() : null;
            if (edge != null && edge.isActive()) {
                edge.writeAndFlush(new DatagramPacket(msg.content().retain(), client));
            } else if (!listens.isEmpty() && listens.get(0).isActive()) {
                listens.get(0).writeAndFlush(new DatagramPacket(msg.content().retain(), client));
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
        private volatile Channel edge;

        Session(Channel channel, InetSocketAddress backend, Channel edge) {
            this.channel = channel;
            this.backend = backend;
            this.edge = edge;
        }

        boolean isActive() {
            return channel.isActive();
        }

        Channel edge() {
            return edge;
        }

        InetSocketAddress backend() {
            return backend;
        }

        void setEdge(Channel edge) {
            this.edge = edge;
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

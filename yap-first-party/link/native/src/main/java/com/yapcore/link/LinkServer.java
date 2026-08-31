package com.yapcore.link;

import com.yapcore.link.backend.BackendMonitor;
import com.yapcore.link.bedrock.BedrockUdpForwarder;
import com.yapcore.link.crypto.MinecraftCrypto;
import com.yapcore.link.chat.ChatRelay;
import com.yapcore.link.console.LinkConsole;
import com.yapcore.link.floodgate.FloodgateForwarder;
import com.yapcore.link.plugin.LinkMetricsImpl;
import com.yapcore.link.plugin.LinkPluginManager;
import com.yapcore.link.protocol.McFrameCodec;
import com.yapcore.link.protocol.McOutboundPacketEncoder;
import com.yapcore.link.ratelimit.ConnectRateGuard;
import com.yapcore.link.metrics.LinkMetricsHttp;
import com.yapcore.link.session.PlayerHub;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.io.IOException;
import java.security.KeyPair;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Netty accept loop for YaP Link.
 * <p>
 * Owns {@link LinkPluginManager}, {@link FloodgateForwarder}, and
 * {@code new BedrockUdpForwarder(config)} (not the legacy 4-arg stub).
 * Plain player chat: {@link ChatRelay} (Phase 2). Backend {@code yap:chat}:
 * {@link com.yapcore.link.api.event.PluginMessageEvent} via {@code yap-link-plugin-chat-bridge}
 * when {@code plugins-enabled=true} (code default is false — see {@link LinkConfig}).
 */
public final class LinkServer {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Server");

    private final AtomicReference<LinkConfig> configRef;
    private final BackendMonitor backendMonitor;
    private final PlayerHub playerHub = new PlayerHub();
    private final ChatRelay chatRelay;
    private final RedirectTokens redirects = new RedirectTokens(60_000L);
    private final KeyPair rsaKeyPair = MinecraftCrypto.generateRsa();
    private final java.util.concurrent.atomic.AtomicInteger online = new java.util.concurrent.atomic.AtomicInteger();
    private final Map<UUID, ClientSession> sessions = new ConcurrentHashMap<>();
    private final LinkPluginManager pluginManager;
    private final FloodgateForwarder floodgate;
    private final LinkMetricsImpl metrics = new LinkMetricsImpl();
    private final ConnectRateGuard rateGuard = new ConnectRateGuard(metrics);
    private LinkMetricsHttp metricsHttp;

    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel bindChannel;
    private BedrockUdpForwarder bedrock;
    private Thread consoleThread;
    private LinkConsole console;

    public LinkServer(LinkConfig config) {
        this.configRef = new AtomicReference<>(config);
        this.backendMonitor = new BackendMonitor(config);
        this.chatRelay = new ChatRelay(this);
        this.floodgate = new FloodgateForwarder(config.floodgateKeyFile());
        this.pluginManager = new LinkPluginManager(this);
    }

    public LinkConfig config() {
        return configRef.get();
    }

    public BackendMonitor backendMonitor() {
        return backendMonitor;
    }

    public PlayerHub playerHub() {
        return playerHub;
    }

    public ChatRelay chatRelay() {
        return chatRelay;
    }

    public RedirectTokens redirects() {
        return redirects;
    }

    public KeyPair rsaKeyPair() {
        return rsaKeyPair;
    }

    public FloodgateForwarder floodgate() {
        return floodgate;
    }

    public LinkPluginManager plugins() {
        return pluginManager;
    }

    public LinkMetricsImpl metrics() {
        return metrics;
    }

    public ConnectRateGuard rateGuard() {
        return rateGuard;
    }

    public Map<UUID, ClientSession> sessions() {
        return sessions;
    }

    void registerSession(UUID id, ClientSession session) {
        sessions.put(id, session);
        metrics.gauge("players.online", sessions.size());
    }

    void unregisterSession(UUID id) {
        sessions.remove(id);
        metrics.gauge("players.online", sessions.size());
    }

    public int onlinePlayers() {
        return online.get();
    }

    void playerJoined() {
        online.incrementAndGet();
        metrics.counter("players.joined", 1);
    }

    void playerLeft() {
        online.updateAndGet(v -> Math.max(0, v - 1));
        metrics.counter("players.left", 1);
    }

    /** Deliver plugin message bytes to all connections on a backend (best-effort). */
    public void broadcastToBackend(String backendName, com.yapcore.link.api.ChannelIdentifier channel, byte[] data) {
        for (ClientSession s : sessions.values()) {
            if (backendName.equalsIgnoreCase(s.backendName())) {
                s.sendBackendPluginMessage(channel, data);
            }
        }
    }

    public synchronized void start() throws InterruptedException {
        if (bindChannel != null) {
            return;
        }
        backendMonitor.start();
        if (configRef.get().pluginsEnabled()) {
            pluginManager.loadAll();
            LOG.info("YaP Link plugins loaded: " + pluginManager.loadedCount());
        }
        boss = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();
        LinkConfig cfg = configRef.get();
        ServerBootstrap b = new ServerBootstrap();
        b.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, cfg.connectTimeoutMs())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        String ip = "unknown";
                        if (ch.remoteAddress() instanceof java.net.InetSocketAddress isa) {
                            ip = isa.getAddress().getHostAddress();
                        }
                        LinkConfig live = configRef.get();
                        if (!rateGuard.allowConnect(ip, live)) {
                            ch.close();
                            return;
                        }
                        if (!rateGuard.tryAcquireConcurrent(ip, live)) {
                            ch.close();
                            return;
                        }
                        final String trackedIp = ip;
                        ch.closeFuture().addListener(f -> rateGuard.releaseConcurrent(trackedIp));
                        int readTimeout = live.readTimeoutSec();
                        if (readTimeout > 0) {
                            ch.pipeline().addLast("read-timeout",
                                    new ReadTimeoutHandler(readTimeout));
                        }
                        ch.pipeline()
                                .addLast("frame-dec", new McFrameCodec.Decoder())
                                .addLast("frame-enc", new McOutboundPacketEncoder())
                                .addLast("client", new ClientSession(LinkServer.this));
                    }
                });
        bindChannel = b.bind(cfg.bindHost(), cfg.bindPort()).sync().channel();
        LOG.info("JE listening on " + cfg.bindHost() + ":" + cfg.bindPort()
                + " online-mode=" + cfg.onlineMode()
                + " ping-passthrough=" + cfg.pingPassthrough()
                + " floodgate=" + floodgate.enabled()
                + " plugins=" + pluginManager.loadedCount()
                + " rate-limit=" + cfg.connectRateLimitEnabled());

        if (cfg.metricsHttpEnabled() && cfg.metricsHttpPort() > 0) {
            try {
                metricsHttp = new LinkMetricsHttp(this);
                metricsHttp.start(cfg.metricsHttpBind(), cfg.metricsHttpPort());
            } catch (IOException e) {
                LOG.log(Level.WARNING, "metrics HTTP failed: " + e.getMessage(), e);
            }
        }

        if (cfg.bedrockEnabled()) {
            bedrock = new BedrockUdpForwarder(cfg);
            bedrock.start();
        }

        console = new LinkConsole(this);
        consoleThread = new Thread(console, "yap-link-console");
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    public synchronized void reloadConfig() throws IOException {
        LinkConfig next = LinkConfig.load(configRef.get().home());
        configRef.set(next);
        backendMonitor.updateConfig(next);
        pluginManager.reloadServerRegistry();
        LOG.info("Reloaded config — servers=" + next.servers().keySet()
                + " try=" + next.tryOrder());
    }

    public synchronized void stop() {
        if (metricsHttp != null) {
            metricsHttp.stop();
            metricsHttp = null;
        }
        if (console != null) {
            console.stop();
            console = null;
        }
        pluginManager.disableAll();
        backendMonitor.stop();
        if (bedrock != null) {
            bedrock.stop();
            bedrock = null;
        }
        try {
            if (bindChannel != null) {
                bindChannel.close().syncUninterruptibly();
                bindChannel = null;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "bind close", e);
        }
        if (worker != null) {
            worker.shutdownGracefully();
            worker = null;
        }
        if (boss != null) {
            boss.shutdownGracefully();
            boss = null;
        }
        LOG.info("YaP Link stopped");
    }
}

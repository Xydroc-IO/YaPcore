package com.yapcore.link;

import com.yapcore.link.backend.BackendMonitor;
import com.yapcore.link.bedrock.BedrockUdpForwarder;
import com.yapcore.link.bedrock.session.BedrockNativeConfig;
import com.yapcore.link.bedrock.session.BedrockSessionHost;
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
import java.util.concurrent.TimeUnit;
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
    private BedrockSessionHost bedrockNative;
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
        // Bedrock UDP before JE TCP so shared-port :25565 UDP is up even if something else
        // already holds TCP :25565 (TCP/UDP are independent; phones need UDP first).
        if (cfg.bedrockNativeEnabled()) {
            bedrockNative = new BedrockSessionHost(toNativeConfig(cfg));
            bedrockNative.start();
        } else if (cfg.bedrockEnabled()) {
            if (cfg.geyserEnabled()) {
                LOG.warning("bedrock-mode conflict: forwarder and geyser both requested — starting forwarder only");
            }
            bedrock = new BedrockUdpForwarder(cfg, playerHub::onlineCount);
            bedrock.start();
        } else if (cfg.geyserEnabled()) {
            LOG.info("Bedrock UDP off (bedrock-mode=geyser-backup) — Geyser-Standalone owns :19132");
        }

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
        // Kick JE clients first with a real disconnect packet (not a raw TCP drop).
        java.util.ArrayList<ClientSession> live = new java.util.ArrayList<>(sessions.values());
        for (ClientSession s : live) {
            try {
                s.kick("Proxy restarting — please reconnect");
            } catch (Exception e) {
                LOG.log(Level.FINE, "JE kick on stop", e);
            }
        }
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
        if (bedrockNative != null) {
            bedrockNative.stop();
            bedrockNative = null;
        }
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
        // Quiet period 0 / timeout 1s — default Netty grace waits piled up across
        // boss+worker+Bedrock groups and made GUI Stop feel like ~30s.
        shutdownGroup(worker, "worker");
        worker = null;
        shutdownGroup(boss, "boss");
        boss = null;
        LOG.info("YaP Link stopped");
    }

    private static void shutdownGroup(EventLoopGroup group, String name) {
        if (group == null) {
            return;
        }
        try {
            if (!group.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(2, TimeUnit.SECONDS)) {
                LOG.warning("EventLoopGroup " + name + " shutdown timed out — continuing");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "EventLoopGroup " + name + " shutdown", e);
        }
    }

    private BedrockNativeConfig toNativeConfig(LinkConfig cfg) {
        return new BedrockNativeConfig() {
            @Override
            public java.util.List<java.net.InetSocketAddress> bindAddresses() {
                return cfg.bedrockBindAddresses();
            }

            @Override
            public String motd() {
                return cfg.motd();
            }

            @Override
            public int maxPlayers() {
                // Bedrock MOTD must match JE: sum of UP backends, not static link.properties.
                if (cfg.aggregatePlayerCount() && backendMonitor != null) {
                    return backendMonitor.aggregateStatus().max();
                }
                return cfg.maxPlayers();
            }

            @Override
            public java.util.function.IntSupplier onlineCount() {
                return playerHub::onlineCount;
            }

            @Override
            public java.nio.file.Path linkHome() {
                return cfg.home();
            }

            @Override
            public String floodgateKeyPath() {
                return cfg.floodgateKeyFile().toString();
            }

            @Override
            public java.net.InetSocketAddress javaBackend() {
                // Same picker JE uses — skip a down lobby so Bedrock can still reach survival.
                LinkConfig.Backend target = backendMonitor.pickLoginTarget(null);
                if (target == null) {
                    target = cfg.resolveTry();
                }
                if (target != null && target.host() != null && !target.host().isBlank()) {
                    return new java.net.InetSocketAddress(target.host(), target.port());
                }
                return new java.net.InetSocketAddress(cfg.bedrockBackendHost(), cfg.bedrockBackendPort());
            }

            @Override
            public java.net.InetSocketAddress javaBackendFor(String serverName) {
                if (serverName == null || serverName.isBlank()) {
                    return javaBackend();
                }
                LinkConfig.Backend target = backendMonitor.pickLoginTarget(serverName.trim());
                if (target == null) {
                    target = cfg.findServer(serverName.trim());
                }
                if (target != null && target.host() != null && !target.host().isBlank()) {
                    return new java.net.InetSocketAddress(target.host(), target.port());
                }
                return null;
            }

            @Override
            public String transferHost() {
                String h = cfg.publicHost();
                return h == null || h.isBlank() ? "127.0.0.1" : h.trim();
            }

            @Override
            public int packCdnPort() {
                // public-pack-port in chassis server.properties is mirrored here when set;
                // default 80 so Bedrock CDN matches nginx / Cloudflare, not LAN :8081.
                int p = cfg.intProp("bedrock-pack-cdn-port", 80);
                return p > 0 ? p : 80;
            }

            @Override
            public int motdProtocol() {
                return cfg.bedrockMotdProtocol();
            }

            @Override
            public String motdVersion() {
                return cfg.bedrockMotdVersion();
            }

            @Override
            public String motdSub() {
                return cfg.bedrockMotdSub();
            }

            @Override
            public String fallbackHubServer() {
                return cfg.fallbackServer();
            }

            @Override
            public boolean fallbackOnBackendLoss() {
                return cfg.fallbackOnBackendLoss();
            }
        };
    }
}

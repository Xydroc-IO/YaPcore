package com.yapcore.protocol;

import com.yapcore.client.ClientEdition;
import com.yapcore.client.ClientRegistry;
import com.yapcore.client.ClientSession;
import com.yapcore.config.ServerConfig;
import com.yapcore.crossplay.CrossplayHub;
import com.yapcore.model.GameEvent;
import com.yapcore.network.TrafficCop;
import com.yapcore.protocol.java.JavaProtocolHandler;
import com.yapcore.protocol.java.codec.McFrameCodec;
import com.yapcore.protocol.compat.ProtocolCompat;
import com.yapcore.kernel.JavaKernelProxyHandler;
import com.yapcore.resourcepack.ResourcePackManager;
import com.yapcore.resourcepack.ResourcePackOffer;
import com.yapcore.util.ThreadMetrics;
import com.yaplabs.yapengine.network.traffic.NativeEventLoops;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Dual-stack gateway: Java TCP + Bedrock UDP on a streamlined shared port
 * (when enabled), with Geyser-class crossplay into one world.
 */
public final class DualStackGateway {

    private static final Logger LOG = Logger.getLogger("YaPcore.Gateway");

    private final ServerConfig config;
    private final TrafficCop trafficCop;
    private final ClientRegistry clients;
    private final ProtocolVersionRegistry protocols;
    private final ResourcePackManager packs;
    private final CrossplayHub crossplay;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean proxyToGameKernel;

    private NativeEventLoops.Transport javaTransport;
    private EventLoopGroup bedrockGroup;
    private Channel javaChannel;
    private Channel bedrockChannel;

    public DualStackGateway(ServerConfig config,
                            TrafficCop trafficCop,
                            ClientRegistry clients,
                            ProtocolVersionRegistry protocols,
                            ResourcePackManager packs,
                            CrossplayHub crossplay) {
        this.config = config;
        this.trafficCop = trafficCop;
        this.clients = clients;
        this.protocols = protocols;
        this.packs = packs;
        this.crossplay = crossplay;
    }

    public ClientRegistry getClients() {
        return clients;
    }

    public ProtocolVersionRegistry getProtocols() {
        return protocols;
    }

    public CrossplayHub crossplay() {
        return crossplay;
    }

    /** When true, public JE TCP is raw-proxied to the Mojang game kernel (full vanilla game). */
    public void setProxyToGameKernel(boolean enabled) {
        this.proxyToGameKernel = enabled;
    }

    public boolean isProxyToGameKernel() {
        return proxyToGameKernel;
    }

    public synchronized void start() throws InterruptedException {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        // Java Edition TCP (also used by TrafficCop's primary bind — gateway owns dual-stack extras)
        if (config.isJavaEnabled() && config.isYaPcoreJavaListener()) {
            javaTransport = NativeEventLoops.create(1, 2);
            final boolean kernelProxy = proxyToGameKernel;
            ServerBootstrap boot = new ServerBootstrap();
            boot.group(javaTransport.boss(), javaTransport.worker())
                    .channel(javaTransport.serverChannelClass())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.AUTO_READ, !kernelProxy)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            if (kernelProxy) {
                                ch.pipeline().addLast("kernel-proxy", new JavaKernelProxyHandler(
                                        "127.0.0.1",
                                        config.getWrappedGamePort(),
                                        javaTransport.worker()));
                            } else {
                                ch.pipeline()
                                        .addLast("frame-dec", new McFrameCodec.Decoder())
                                        .addLast("frame-enc", new McFrameCodec.Encoder())
                                        .addLast("je", new JavaProtocolHandler(
                                                DualStackGateway.this, config, protocols));
                            }
                        }
                    });
            int javaPort = config.getPort();
            try {
                javaChannel = boot.bind(config.getBindHost().equals("0.0.0.0") ? "0.0.0.0" : config.getBindHost(),
                        javaPort).sync().channel();
                if (kernelProxy) {
                    LOG.info("Java Edition listener on :" + javaPort
                            + " transport=" + javaTransport.kind()
                            + " | mode=WRAPPED_GAME_PROXY → 127.0.0.1:" + config.getWrappedGamePort()
                            + " (authority=" + config.getGameAuthority() + ")");
                } else {
                    LOG.info("Java Edition listener on :" + javaPort
                            + " transport=" + javaTransport.kind()
                            + " | multi-version=" + ProtocolCompat.isOnline()
                            + " (backwards-compat=" + protocols.isLenient() + ")");
                }
                if (config.isAllowLocalhost()) {
                    LOG.info("Same-PC clients: connect to 127.0.0.1:" + javaPort
                            + " or localhost:" + javaPort);
                }
            } catch (Exception e) {
                LOG.info("Java TCP port " + javaPort + " bind note: " + e.getMessage());
                if (javaTransport != null) {
                    javaTransport.shutdown();
                    javaTransport = null;
                }
            }
            ThreadMetrics.record("Gateway", "java-online");
        } else if (config.isJavaEnabled() && config.isPaperAuthority() && config.isPaperEmbed()) {
            LOG.info("Java Edition TCP owned by Paper on :" + config.getPort()
                    + " (YaPcore JE listener/proxy retired)");
        }

        if (config.isBedrockEnabled()) {
            bedrockGroup = new NioEventLoopGroup(2);
            Bootstrap udp = new Bootstrap();
            udp.group(bedrockGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .handler(new BedrockUdpHandler());
            int bedrockPort = config.effectiveBedrockPort();
            try {
                bedrockChannel = udp.bind(
                        config.getBindHost().equals("0.0.0.0") ? "0.0.0.0" : config.getBindHost(),
                        bedrockPort).sync().channel();
                if (config.isSharedListenPort()) {
                    LOG.info("Bedrock Edition UDP on :" + bedrockPort
                            + " (shared with Java TCP — one join port)");
                } else {
                    LOG.info("Bedrock Edition UDP listener on :" + bedrockPort
                            + " (backwards-compat=" + protocols.isLenient() + ")");
                }
                ThreadMetrics.record("Gateway", "bedrock-online");
            } catch (Exception e) {
                LOG.warning("Bedrock UDP port " + bedrockPort + " bind failed: " + e.getMessage());
                if (bedrockGroup != null) {
                    bedrockGroup.shutdownGracefully();
                    bedrockGroup = null;
                }
            }
        }

        LOG.info("Dual-stack gateway ready — Java=" + config.isJavaEnabled()
                + " Bedrock=" + config.isBedrockEnabled()
                + " shared-port=" + config.isSharedListenPort()
                + " crossplay=" + config.isCrossplayEnabled()
                + " resource-packs=" + config.isResourcePackEnabled());
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (javaChannel != null) {
            javaChannel.close();
        }
        if (bedrockChannel != null) {
            bedrockChannel.close();
        }
        shutdownJavaGroups();
        if (bedrockGroup != null) {
            bedrockGroup.shutdownGracefully();
            bedrockGroup = null;
        }
        clients.clear();
        if (crossplay != null) {
            crossplay.clear();
        }
        ThreadMetrics.record("Gateway", "stopped");
    }

    private void shutdownJavaGroups() {
        if (javaTransport != null) {
            javaTransport.shutdown();
            javaTransport = null;
        }
    }

    /**
     * Programmatic join used by demos / TrafficCop line protocol / Bedrock handshakes.
     */
    public ClientSession acceptClient(String username,
                                      ClientEdition edition,
                                      int protocolId,
                                      InetSocketAddress address) {
        Optional<ProtocolVersionRegistry.ProtocolVersion> resolved =
                protocols.resolve(edition, protocolId);
        if (resolved.isEmpty()) {
            LOG.warning("Rejected " + edition + " client " + username
                    + " unsupported protocol " + protocolId);
            trafficCop.ingest(new GameEvent(GameEvent.Type.CLIENT_REJECTED, username, Map.of(
                    "edition", edition.name(),
                    "protocol", Integer.toString(protocolId),
                    "reason", "unsupported-protocol"
            )));
            return null;
        }
        ProtocolVersionRegistry.ProtocolVersion ver = resolved.get();
        if (ver.protocolId() != protocolId) {
            LOG.info("Back-compat map " + edition + " protocol " + protocolId
                    + " → " + ver.protocolId() + " (" + ver.minecraftVersion() + ")");
        }
        ClientSession session = new ClientSession(username, edition, ver, address);
        clients.register(session);
        if (config.isCrossplayEnabled() && crossplay != null) {
            crossplay.join(session);
        }
        trafficCop.ingest(new GameEvent(GameEvent.Type.CLIENT_JOIN, username, Map.of(
                "edition", edition.name(),
                "protocol", Integer.toString(ver.protocolId()),
                "mc-version", ver.minecraftVersion(),
                "session", session.getSessionId().toString(),
                "crossplay", Boolean.toString(config.isCrossplayEnabled()),
                "shared-port", Boolean.toString(config.isSharedListenPort())
        )));

        Optional<ResourcePackOffer> offer = packs.createOffer(session);
        offer.ifPresent(o -> trafficCop.ingest(new GameEvent(
                GameEvent.Type.RESOURCE_PACK_OFFER,
                username,
                Map.of(
                        "url", o.url(),
                        "sha1", o.sha1Hex(),
                        "forced", Boolean.toString(o.forced()),
                        "prompt", o.prompt(),
                        "edition", edition.name()
                )
        )));
        return session;
    }

    public void handlePackStatus(String username, ClientSession.ResourcePackState state) {
        clients.get(username).ifPresent(session -> {
            session.setPackState(state);
            trafficCop.ingest(new GameEvent(GameEvent.Type.RESOURCE_PACK_STATUS, username, Map.of(
                    "state", state.name(),
                    "edition", session.getEdition().name()
            )));
            LOG.info("Resource pack status from " + username + ": " + state);
        });
    }

    /**
     * Legacy line-protocol probe (tests / tooling). Vanilla clients use
     * {@link com.yapcore.protocol.java.JavaProtocolHandler} instead.
     */
    @Deprecated
    private final class JavaClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            String raw = msg.toString(CharsetUtil.UTF_8).trim();
            if (raw.isEmpty()) {
                return;
            }
            InetSocketAddress addr = (InetSocketAddress) ctx.channel().remoteAddress();
            String[] parts = raw.split("\\|");
            String cmd = parts[0].trim().toUpperCase();
            switch (cmd) {
                case "JOIN" -> {
                    String user = parts.length > 1 ? parts[1] : "JavaPlayer";
                    int proto = parts.length > 2 ? parseInt(parts[2], protocols.recommended(ClientEdition.JAVA).protocolId()) : protocols.recommended(ClientEdition.JAVA).protocolId();
                    ClientSession session = acceptClient(user, ClientEdition.JAVA, proto, addr);
                    if (session != null) {
                        String reply = "OK|JAVA|" + session.getProtocol().minecraftVersion()
                                + "|pack=" + session.getActiveOffer().map(ResourcePackOffer::url).orElse("none")
                                + "\n";
                        ctx.writeAndFlush(Unpooled.copiedBuffer(reply, StandardCharsets.UTF_8));
                    } else {
                        ctx.writeAndFlush(Unpooled.copiedBuffer("ERR|unsupported\n", StandardCharsets.UTF_8));
                    }
                }
                case "PACK" -> {
                    String user = parts.length > 1 ? parts[1] : "JavaPlayer";
                    String state = parts.length > 2 ? parts[2] : "LOADED";
                    try {
                        handlePackStatus(user, ClientSession.ResourcePackState.valueOf(state.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        handlePackStatus(user, ClientSession.ResourcePackState.FAILED);
                    }
                }
                case "LEAVE" -> {
                    String user = parts.length > 1 ? parts[1] : "JavaPlayer";
                    clients.get(user).ifPresent(s -> {
                        if (crossplay != null) {
                            crossplay.leave(s);
                        }
                        clients.unregister(s);
                    });
                    trafficCop.ingest(new GameEvent(GameEvent.Type.CLIENT_LEAVE, user, Map.of(
                            "edition", "JAVA"
                    )));
                }
                case "MOVE", "CHAT", "INTERACT", "CLICK" -> {
                    String user = parts.length > 1 ? parts[1] : "JavaPlayer";
                    clients.get(user).ifPresent(s -> {
                        if (crossplay != null) {
                            java.util.concurrent.ConcurrentHashMap<String, String> map =
                                    new java.util.concurrent.ConcurrentHashMap<>();
                            for (int i = 2; i + 1 < parts.length; i += 2) {
                                map.put(parts[i], parts[i + 1]);
                            }
                            crossplay.handleAction(s, cmd, map);
                        }
                    });
                }
                default -> trafficCop.ingest(TrafficCop.parsePacket(raw));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    /**
     * Bedrock UDP: responds to unconnected pings and accepts JOIN text datagrams.
     * Full RakNet gameplay framing can plug into this handler later.
     */
    private final class BedrockUdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            ByteBuf content = packet.content();
            InetSocketAddress sender = packet.sender();

            // RakNet Unconnected Ping starts with 0x01
            if (content.readableBytes() > 0 && (content.getByte(content.readerIndex()) & 0xFF) == 0x01) {
                ByteBuf pong = buildBedrockPong();
                ctx.writeAndFlush(new DatagramPacket(pong, sender));
                ThreadMetrics.bump("Gateway", "bedrock-ping");
                return;
            }

            String raw = content.toString(CharsetUtil.UTF_8).trim();
            if (raw.isEmpty()) {
                return;
            }
            String[] parts = raw.split("\\|");
            String cmd = parts[0].trim().toUpperCase();
            if ("JOIN".equals(cmd)) {
                String user = parts.length > 1 ? parts[1] : "BedrockPlayer";
                int proto = parts.length > 2 ? parseInt(parts[2], protocols.recommended(ClientEdition.BEDROCK).protocolId())
                        : protocols.recommended(ClientEdition.BEDROCK).protocolId();
                ClientSession session = acceptClient(user, ClientEdition.BEDROCK, proto, sender);
                if (session != null) {
                    String reply = "OK|BEDROCK|" + session.getProtocol().minecraftVersion()
                            + "|pack=" + session.getActiveOffer().map(ResourcePackOffer::url).orElse("none");
                    ctx.writeAndFlush(new DatagramPacket(
                            Unpooled.copiedBuffer(reply, StandardCharsets.UTF_8), sender));
                }
            } else if ("PACK".equals(cmd)) {
                String user = parts.length > 1 ? parts[1] : "BedrockPlayer";
                String state = parts.length > 2 ? parts[2] : "LOADED";
                try {
                    handlePackStatus(user, ClientSession.ResourcePackState.valueOf(state.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    handlePackStatus(user, ClientSession.ResourcePackState.FAILED);
                }
            } else if ("LEAVE".equals(cmd)) {
                String user = parts.length > 1 ? parts[1] : "BedrockPlayer";
                clients.get(user).ifPresent(s -> {
                    if (crossplay != null) {
                        crossplay.leave(s);
                    }
                    clients.unregister(s);
                });
                trafficCop.ingest(new GameEvent(GameEvent.Type.CLIENT_LEAVE, user, Map.of(
                        "edition", "BEDROCK"
                )));
            } else if ("MOVE".equals(cmd) || "CHAT".equals(cmd) || "INTERACT".equals(cmd)) {
                String user = parts.length > 1 ? parts[1] : "BedrockPlayer";
                clients.get(user).ifPresent(s -> {
                    if (crossplay != null) {
                        java.util.concurrent.ConcurrentHashMap<String, String> map =
                                new java.util.concurrent.ConcurrentHashMap<>();
                        for (int i = 2; i + 1 < parts.length; i += 2) {
                            map.put(parts[i], parts[i + 1]);
                        }
                        // Bedrock lane → Geyser-style translator → shared world
                        crossplay.handleAction(s, cmd, map);
                    }
                });
            }
        }

        private ByteBuf buildBedrockPong() {
            // Minimal unconnected pong-like payload with MOTD for server lists
            String motd = "MCPE;" + config.getMotd().replace(';', ' ') + ";"
                    + protocols.recommended(ClientEdition.BEDROCK).protocolId() + ";"
                    + protocols.recommended(ClientEdition.BEDROCK).minecraftVersion() + ";"
                    + clients.size() + ";"
                    + config.getMaxPlayers() + ";"
                    + "YaPcore;Survival;";
            byte[] bytes = motd.getBytes(StandardCharsets.UTF_8);
            ByteBuf buf = Unpooled.buffer(1 + 8 + 8 + 16 + 2 + bytes.length);
            buf.writeByte(0x1C); // Unconnected Pong
            buf.writeLong(System.currentTimeMillis());
            buf.writeLong(0x000000000003L);
            buf.writeBytes(new byte[16]); // magic placeholder
            buf.writeShort(bytes.length);
            buf.writeBytes(bytes);
            return buf;
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}

package com.yapcore.protocol;

import com.yapcore.client.ClientEdition;
import com.yapcore.client.ClientRegistry;
import com.yapcore.client.ClientSession;
import com.yapcore.config.ServerConfig;
import com.yapcore.crossplay.CrossplayHub;
import com.yapcore.crossplay.raknet.RakNetUnconnected;
import com.yapcore.crossplay.raknet.RakNetReliability;
import com.yapcore.crossplay.raknet.RakNetSessionManager;
import com.yapcore.crossplay.bedrock.BedrockSessionManager;
import com.yapcore.crossplay.bedrock.BedrockGameplayBridge;
import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.crossplay.form.FormService;
import com.yapcore.crossplay.skin.SkinService;
import com.yapcore.model.GameEvent;
import com.yapcore.network.TrafficCop;
import com.yapcore.protocol.java.JavaProtocolHandler;
import com.yapcore.protocol.java.codec.McFrameCodec;
import com.yapcore.protocol.compat.ProtocolCompat;
import com.yapcore.protocol.via.ViaProxyHandler;
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
import java.util.concurrent.ConcurrentHashMap;
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
    private final BedrockSessionManager bedrockSessions = new BedrockSessionManager();
    private final FloodgateAuth floodgateAuth = new FloodgateAuth();
    private final SkinService skinService = new SkinService();
    private final FormService formService = new FormService();
    private final BedrockGameplayBridge bedrockBridge;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean proxyToGameKernel;
    private volatile RakNetSessionManager rakNetSessions;

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
        this.bedrockBridge = new BedrockGameplayBridge(
                bedrockSessions, floodgateAuth, skinService, formService);
        if (crossplay != null) {
            crossplay.attachFloodgate(floodgateAuth, skinService, formService);
        }
    }

    public FloodgateAuth floodgateAuth() {
        return floodgateAuth;
    }

    public SkinService skinService() {
        return skinService;
    }

    public FormService formService() {
        return formService;
    }

    public BedrockGameplayBridge bedrockBridge() {
        return bedrockBridge;
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

    public BedrockSessionManager bedrockSessions() {
        return bedrockSessions;
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
                                if (config.isProtocolViaEnabled() && config.isPaperAuthority()) {
                                    ch.pipeline().addLast("via-proxy", new ViaProxyHandler(
                                            "127.0.0.1",
                                            config.paperListenPort(),
                                            ProtocolCompat.SERVER_PROTOCOL));
                                } else {
                                    ch.pipeline().addLast("kernel-proxy", new JavaKernelProxyHandler(
                                            "127.0.0.1",
                                            config.getWrappedGamePort(),
                                            javaTransport.worker()));
                                }
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
                    boolean via = config.isProtocolViaEnabled() && config.isPaperAuthority();
                    LOG.info("Java Edition listener on :" + javaPort
                            + " transport=" + javaTransport.kind()
                            + (via
                            ? " | mode=VIA_PROXY → 127.0.0.1:" + config.paperListenPort()
                            + " (Via parity, serverProto=" + ProtocolCompat.SERVER_PROTOCOL + ")"
                            : " | mode=WRAPPED_GAME_PROXY → 127.0.0.1:" + config.getWrappedGamePort()
                            + " (authority=" + config.getGameAuthority() + ")"));
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

        var offers = packs.createOffers(session);
        if (!offers.isEmpty()) {
            ResourcePackOffer o = offers.get(0);
            trafficCop.ingest(new GameEvent(
                    GameEvent.Type.RESOURCE_PACK_OFFER,
                    username,
                    Map.of(
                            "url", o.url(),
                            "sha1", o.sha1Hex(),
                            "forced", Boolean.toString(o.forced()),
                            "prompt", o.prompt(),
                            "edition", edition.name(),
                            "count", Integer.toString(offers.size())
                    )
            ));
        }
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
     * Bedrock UDP: RakNet reliability + BE gameplay codecs + text JOIN fallback (Geyser 4.G1–G4).
     */
    private final class BedrockUdpHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final long serverGuid = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        private final RakNetSessionManager rakNet = new RakNetSessionManager(serverGuid);
        private final ConcurrentHashMap<Long, InetSocketAddress> guidToAddr = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> addrToGuid = new ConcurrentHashMap<>();

        BedrockUdpHandler() {
            DualStackGateway.this.rakNetSessions = rakNet;
            bedrockBridge.setCompressionArmed(guid -> {
                InetSocketAddress addr = guidToAddr.get(guid);
                if (addr != null) {
                    rakNet.peer(addr).setGameCompressionHeader(true);
                }
            });
            bedrockBridge.setOutbound((guid, packets) -> {
                InetSocketAddress addr = guidToAddr.get(guid);
                if (addr == null || bedrockChannel == null) {
                    LOG.warning(
                            "BE outbound drop guid=" + Long.toHexString(guid)
                                    + " addr=" + addr + " ch=" + (bedrockChannel != null));
                    for (ByteBuf pkt : packets) {
                        pkt.release();
                    }
                    return;
                }
                RakNetSessionManager.RakNetPeer peer = rakNet.peer(addr);
                for (ByteBuf pkt : packets) {
                    ByteBuf batch = Unpooled.buffer(pkt.readableBytes() + 8);
                    BedrockPacketCodec.writeUnsignedVarInt(batch, pkt.readableBytes());
                    batch.writeBytes(pkt);
                    pkt.release();
                    for (ByteBuf framed : rakNet.encapsulateGameDatagrams(peer, batch)) {
                        bedrockChannel.writeAndFlush(new DatagramPacket(framed, addr));
                    }
                    batch.release();
                }
            });
            formService.setSender((user, buf) -> {
                BedrockSessionManager.BedrockSession s = bedrockSessions.byUsername(user);
                if (s == null) {
                    buf.release();
                    return;
                }
                InetSocketAddress addr = guidToAddr.get(s.guid());
                if (addr == null || bedrockChannel == null) {
                    buf.release();
                    return;
                }
                ByteBuf batch = Unpooled.buffer(buf.readableBytes() + 8);
                BedrockPacketCodec.writeUnsignedVarInt(batch, buf.readableBytes());
                batch.writeBytes(buf);
                buf.release();
                for (ByteBuf framed : rakNet.encapsulateGameDatagrams(rakNet.peer(addr), batch)) {
                    bedrockChannel.writeAndFlush(new DatagramPacket(framed, addr));
                }
                batch.release();
            });
            rakNet.setGamePacketHandler((peer, gameBatch) -> {
                long guid = peer.state().clientGuid();
                if (guid == 0L) {
                    guid = peer.address().hashCode();
                }
                guidToAddr.put(guid, peer.address());
                addrToGuid.put(peer.address().toString(), guid);
                var actions = bedrockBridge.onGameBatch(guid, peer.address().toString(), gameBatch);
                gameBatch.release();
                for (var action : actions) {
                    applyBedrockAction(action.type(), action.username(), action.payload(), peer.address());
                }
            });
            rakNet.setDisconnectHandler(peer -> {
                long guid = peer.state().clientGuid();
                if (guid == 0L) {
                    guid = peer.address().hashCode();
                }
                var session = bedrockSessions.get(guid);
                String user = session != null ? session.username() : null;
                bedrockBridge.onDisconnect(guid);
                if (user != null) {
                    clients.get(user).ifPresent(s -> {
                        if (crossplay != null) {
                            crossplay.leave(s);
                        }
                        clients.unregister(s);
                    });
                    trafficCop.ingest(new GameEvent(GameEvent.Type.CLIENT_LEAVE, user, Map.of(
                            "edition", "BEDROCK"
                    )));
                }
                guidToAddr.remove(guid);
                addrToGuid.remove(peer.address().toString());
            });
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            ByteBuf content = packet.content();
            InetSocketAddress sender = packet.sender();

            if (config.isProtocolGeyserEnabled() && RakNetUnconnected.isUnconnectedPing(content)) {
                long pingTime = 0L;
                try {
                    pingTime = RakNetUnconnected.readPingTime(content);
                } catch (Exception ignored) {
                    pingTime = System.currentTimeMillis();
                }
                String motd = "MCPE;" + config.getMotd().replace(';', ' ') + ";"
                        + protocols.recommended(ClientEdition.BEDROCK).protocolId() + ";"
                        + protocols.recommended(ClientEdition.BEDROCK).minecraftVersion() + ";"
                        + clients.size() + ";"
                        + config.getMaxPlayers() + ";"
                        + Long.toUnsignedString(serverGuid) + ";"
                        + "YaPcore;Survival;";
                ByteBuf pong = RakNetUnconnected.buildPong(pingTime, serverGuid, motd);
                ctx.writeAndFlush(new DatagramPacket(pong, sender));
                ThreadMetrics.bump("Gateway", "bedrock-ping");
                return;
            }

            if (config.isProtocolGeyserEnabled()) {
                int id = content.isReadable() ? content.getUnsignedByte(content.readerIndex()) : -1;
                if (id == RakNetReliability.ID_OPEN_CONNECTION_REQUEST_1
                        || id == RakNetReliability.ID_OPEN_CONNECTION_REQUEST_2
                        || RakNetReliability.isFrameSet(id)
                        || id == RakNetReliability.ID_ACK
                        || id == RakNetReliability.ID_NACK) {
                    // retainedDuplicate: handle() may retain frame slices past this read
                    ByteBuf copy = content.retainedDuplicate();
                    try {
                        for (ByteBuf reply : rakNet.handle(sender, copy)) {
                            ctx.writeAndFlush(new DatagramPacket(reply, sender));
                        }
                    } finally {
                        copy.release();
                    }
                    ThreadMetrics.bump("Gateway", "bedrock-raknet");
                    return;
                }
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
                long guid = sender.hashCode() * 31L + user.hashCode();
                FloodgateAuth.Identity identity = floodgateAuth.register(new FloodgateAuth.Identity(
                        user,
                        FloodgateAuth.uuidFromXuid(user + "|" + sender).toString().replace("-", "").substring(0, 16),
                        FloodgateAuth.uuidFromXuid(user + "|" + sender),
                        proto,
                        false,
                        ""));
                bedrockSessions.open(guid, user, proto, sender.toString());
                skinService.registerDefault(user, identity.javaUuid());
                guidToAddr.put(guid, sender);
                ClientSession session = acceptClient(user, ClientEdition.BEDROCK, proto, sender);
                if (session != null) {
                    String reply = "OK|BEDROCK|" + session.getProtocol().minecraftVersion()
                            + "|pack=" + session.getActiveOffer().map(ResourcePackOffer::url).orElse("none")
                            + "|geyser=yap|floodgate=" + identity.javaUuid();
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
                BedrockSessionManager.BedrockSession bs = bedrockSessions.byUsername(user);
                if (bs != null) {
                    bedrockBridge.onDisconnect(bs.guid());
                }
                clients.get(user).ifPresent(s -> {
                    if (crossplay != null) {
                        crossplay.leave(s);
                    }
                    clients.unregister(s);
                });
                trafficCop.ingest(new GameEvent(GameEvent.Type.CLIENT_LEAVE, user, Map.of(
                        "edition", "BEDROCK"
                )));
            } else if ("MOVE".equals(cmd) || "CHAT".equals(cmd) || "INTERACT".equals(cmd)
                    || "BREAK".equals(cmd) || "PLACE".equals(cmd) || "ATTACK".equals(cmd)
                    || "INV".equals(cmd) || "HOTBAR".equals(cmd)
                    || "FORM".equals(cmd) || "SKIN".equals(cmd)) {
                String user = parts.length > 1 ? parts[1] : "BedrockPlayer";
                if ("FORM".equals(cmd) && parts.length > 2) {
                    formService.sendSimple(user, "YaPcore", parts[2], "OK", "Cancel");
                    return;
                }
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
        }

        private void applyBedrockAction(String type, String user, Map<String, String> payload,
                                        InetSocketAddress sender) {
            if ("JOIN".equalsIgnoreCase(type)) {
                int proto = parseInt(payload.getOrDefault("protocol",
                        Integer.toString(protocols.recommended(ClientEdition.BEDROCK).protocolId())),
                        protocols.recommended(ClientEdition.BEDROCK).protocolId());
                acceptClient(user, ClientEdition.BEDROCK, proto, sender);
                return;
            }
            clients.get(user).ifPresent(s -> {
                if (crossplay != null) {
                    crossplay.handleAction(s, type, payload);
                }
            });
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

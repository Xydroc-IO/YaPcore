package com.yapcore.link;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.yapcore.link.auth.MojangAuth;
import com.yapcore.link.backend.BackendMonitor;
import com.yapcore.link.floodgate.FloodgateForwarder;
import com.yapcore.link.plugin.LinkPlayerImpl;
import com.yapcore.link.plugin.RegisteredServerImpl;
import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.event.DisconnectEvent;
import com.yapcore.link.api.event.LoginEvent;
import com.yapcore.link.api.event.PingEvent;
import com.yapcore.link.api.event.PostConnectEvent;
import com.yapcore.link.api.event.PreConnectEvent;
import com.yapcore.link.api.event.ServerChooseEvent;
import com.yapcore.link.chat.ChatRelay;
import com.yapcore.link.crypto.MinecraftCrypto;
import com.yapcore.link.forwarding.ModernForwarding;
import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.protocol.McCompressionCodec;
import com.yapcore.link.protocol.McFrameCodec;
import com.yapcore.link.protocol.PlayChat;
import com.yapcore.link.protocol.PluginMessagePackets;
import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.event.PluginMessageEvent;
import com.yapcore.link.session.PlayerHub;
import com.yapcore.link.status.ServerStatus;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutException;

import javax.crypto.Cipher;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client connection: status / login → modern-forward backend → bridge.
 * Phases 0–2: passthrough ping, forced hosts, health failover, chat relay, system chat.
 */
public final class ClientSession extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Client");
    private static final Gson GSON = new Gson();

    private enum Phase {
        HANDSHAKE, STATUS, LOGIN_CLIENT, LOGIN_ENCRYPT, CONNECTING_BACKEND, BRIDGING
    }

    private final LinkServer server;
    private Phase phase = Phase.HANDSHAKE;
    private int protocolVersion;
    private String username;
    private UUID playerId;
    private List<ModernForwarding.Property> properties = List.of();
    private String clientAddress = "127.0.0.1";
    private String virtualHost = "";
    private String forcedServerName;
    private Channel backend;
    private String currentBackendName;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private boolean counted;
    private final byte[] verifyToken = new byte[4];
    private ChannelHandlerContext clientCtx;
    private long loginStartedMs;
    private LinkPlayerImpl playerHandle;
    private String floodgatePayload;

    public ClientSession(LinkServer server) {
        this.server = server;
        new SecureRandom().nextBytes(verifyToken);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.clientCtx = ctx;
        loginStartedMs = System.currentTimeMillis();
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress isa) {
            clientAddress = isa.getAddress().getHostAddress();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        teardown(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof ReadTimeoutException) {
            LOG.fine("read timeout " + clientAddress);
        } else {
            LOG.log(Level.FINE, "client error: " + cause.getMessage(), cause);
        }
        teardown(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            return;
        }
        try {
            if (phase == Phase.LOGIN_CLIENT || phase == Phase.LOGIN_ENCRYPT || phase == Phase.CONNECTING_BACKEND) {
                checkLoginTimeout();
            }
            switch (phase) {
                case HANDSHAKE -> handleHandshake(ctx, buf);
                case STATUS -> handleStatus(ctx, buf);
                case LOGIN_CLIENT -> handleLoginStart(ctx, buf);
                case LOGIN_ENCRYPT -> handleEncryptionResponse(ctx, buf);
                case CONNECTING_BACKEND, BRIDGING -> buf.release();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "client packet failed", e);
            kick(ctx, "Proxy error: " + e.getMessage());
        }
    }

    private void checkLoginTimeout() {
        LinkConfig cfg = server.config();
        if (System.currentTimeMillis() - loginStartedMs > cfg.loginTimeoutMs()) {
            throw new IllegalStateException("Login timed out");
        }
    }

    private void handleHandshake(ChannelHandlerContext ctx, ByteBuf buf) {
        int packetId = McCodec.readVarInt(buf);
        if (packetId != 0x00) {
            buf.release();
            ctx.close();
            return;
        }
        protocolVersion = McCodec.readVarInt(buf);
        virtualHost = McCodec.readString(buf, 255);
        buf.readUnsignedShort();
        int intent = McCodec.readVarInt(buf);
        buf.release();
        forcedServerName = server.config().forcedHostServer(virtualHost);
        floodgatePayload = FloodgateForwarder.extractFloodgatePayload(virtualHost);
        if (forcedServerName != null && server.config().showPingRequests()) {
            LOG.fine("forced-host " + virtualHost + " → " + forcedServerName);
        }
        if (intent == 1) {
            phase = Phase.STATUS;
        } else if (intent == 2) {
            phase = Phase.LOGIN_CLIENT;
        } else {
            ctx.close();
        }
    }

    private void handleStatus(ChannelHandlerContext ctx, ByteBuf buf) {
        int packetId = McCodec.readVarInt(buf);
        if (packetId == 0x00) {
            buf.release();
            ByteBuf resp = Unpooled.buffer();
            McCodec.writeVarInt(resp, 0x00);
            McCodec.writeString(resp, statusJson());
            ctx.writeAndFlush(resp);
        } else if (packetId == 0x01) {
            long payload = buf.readLong();
            buf.release();
            ByteBuf pong = Unpooled.buffer();
            McCodec.writeVarInt(pong, 0x01);
            pong.writeLong(payload);
            ctx.writeAndFlush(pong);
        } else {
            buf.release();
        }
    }

    private String statusJson() {
        LinkConfig cfg = server.config();
        BackendMonitor mon = server.backendMonitor();
        String json;
        if (cfg.pingPassthrough()) {
            if (forcedServerName != null) {
                BackendMonitor.Snapshot snap = mon.snapshot(forcedServerName);
                if (snap.up() && snap.status() != null) {
                    json = snap.status().rawJson();
                } else {
                    json = fallbackStatus(cfg, mon);
                }
            } else {
                json = fallbackStatus(cfg, mon);
            }
        } else {
            json = ServerStatus.synthetic(
                    cfg.motd(),
                    server.playerHub().onlineCount(),
                    cfg.maxPlayers(),
                    protocolVersion > 0 ? protocolVersion : 776,
                    "YaP Link"
            ).rawJson();
        }
        PingEvent ping = new PingEvent(protocolVersion, json);
        server.plugins().eventBus().fire(ping);
        return ping.statusJson();
    }

    private String fallbackStatus(LinkConfig cfg, BackendMonitor mon) {
        ServerStatus agg = mon.aggregateStatus();
        if (cfg.aggregatePlayerCount() && agg.online() >= 0) {
            int online = sumOnline(mon);
            int max = Math.max(cfg.maxPlayers(), agg.max());
            return agg.toStatusJson(online, max);
        }
        if (agg.rawJson() != null && !agg.rawJson().isBlank()) {
            return agg.rawJson();
        }
        return ServerStatus.synthetic(
                cfg.motd(),
                server.playerHub().onlineCount(),
                cfg.maxPlayers(),
                protocolVersion > 0 ? protocolVersion : 776,
                "YaP Link"
        ).rawJson();
    }

    private static int sumOnline(BackendMonitor mon) {
        int n = 0;
        for (var e : mon.allSnapshots().entrySet()) {
            if (e.getValue().up() && e.getValue().status() != null) {
                n += e.getValue().status().online();
            }
        }
        return n;
    }

    private void handleLoginStart(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        int packetId = McCodec.readVarInt(buf);
        if (packetId != 0x00) {
            buf.release();
            kick(ctx, "Unexpected login packet");
            return;
        }
        username = McCodec.readString(buf, 16);
        playerId = readPlayerId(buf);
        buf.release();

        applyFloodgateIdentity();

        InetSocketAddress addr = clientCtx != null && clientCtx.channel().remoteAddress() instanceof InetSocketAddress isa
                ? isa : new InetSocketAddress(clientAddress, 0);
        LoginEvent login = new LoginEvent(playerId, username, addr);
        server.plugins().eventBus().fire(login);
        if (login.isCancelled()) {
            kick(ctx, login.denyReason() != null ? login.denyReason() : "Login denied");
            return;
        }

        if (server.config().onlineMode()) {
            phase = Phase.LOGIN_ENCRYPT;
            sendEncryptionRequest(ctx);
            return;
        }

        String redirect = server.redirects().take(playerId);
        beginBackendConnect(ctx, redirect);
    }

    private UUID readPlayerId(ByteBuf buf) {
        if (buf.readableBytes() >= 16) {
            try {
                return McCodec.readUuid(buf);
            } catch (Exception e) {
                return McCodec.offlineUuid(username);
            }
        }
        return McCodec.offlineUuid(username);
    }

    private void sendEncryptionRequest(ChannelHandlerContext ctx) {
        ByteBuf enc = Unpooled.buffer();
        McCodec.writeVarInt(enc, 0x01);
        McCodec.writeString(enc, "");
        byte[] pub = server.rsaKeyPair().getPublic().getEncoded();
        McCodec.writeVarInt(enc, pub.length);
        enc.writeBytes(pub);
        McCodec.writeVarInt(enc, verifyToken.length);
        enc.writeBytes(verifyToken);
        if (protocolVersion >= 766) {
            enc.writeBoolean(true);
        }
        ctx.writeAndFlush(enc);
    }

    private void handleEncryptionResponse(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        int packetId = McCodec.readVarInt(buf);
        if (packetId != 0x01) {
            buf.release();
            kick(ctx, "Expected encryption response");
            return;
        }
        int secretLen = McCodec.readVarInt(buf);
        byte[] secretEnc = new byte[secretLen];
        buf.readBytes(secretEnc);
        byte[] sharedSecret = MinecraftCrypto.decryptRsa(server.rsaKeyPair(), secretEnc);

        if (protocolVersion >= 766 && buf.isReadable()) {
            boolean hasVerifyToken = buf.readBoolean();
            if (hasVerifyToken) {
                verifyTokenFromBuf(buf);
            } else {
                buf.readLong();
                int sigLen = McCodec.readVarInt(buf);
                buf.skipBytes(Math.min(sigLen, buf.readableBytes()));
            }
        } else {
            verifyTokenFromBuf(buf);
        }
        buf.release();

        Cipher decrypt = MinecraftCrypto.newCipher(Cipher.DECRYPT_MODE, sharedSecret);
        Cipher encrypt = MinecraftCrypto.newCipher(Cipher.ENCRYPT_MODE, sharedSecret);
        ctx.pipeline().addFirst("encrypt", new MinecraftCrypto.CipherCodec(decrypt, encrypt));

        String serverId = MinecraftCrypto.serverId(sharedSecret, server.rsaKeyPair().getPublic());
        MojangAuth.Profile profile = MojangAuth.hasJoined(username, serverId);
        playerId = profile.id();
        username = profile.name();
        properties = profile.properties();
        LOG.info("AUTH ok user=" + username + " uuid=" + playerId + " addr=" + clientAddress);

        applyFloodgateIdentity();
        beginBackendConnect(ctx, server.redirects().take(playerId));
    }

    private void applyFloodgateIdentity() {
        FloodgateForwarder fg = server.floodgate();
        if (!fg.enabled()) {
            return;
        }
        fg.resolve(virtualHost, playerId, username).ifPresent(id -> {
            playerId = id.uuid();
            username = id.username();
            LOG.info("Floodgate identity " + username + " xuid=" + id.xuid() + " linked=" + id.linked());
        });
    }

    private void verifyTokenFromBuf(ByteBuf buf) throws Exception {
        int tokenLen = McCodec.readVarInt(buf);
        byte[] tokenEnc = new byte[tokenLen];
        buf.readBytes(tokenEnc);
        byte[] token = MinecraftCrypto.decryptRsa(server.rsaKeyPair(), tokenEnc);
        if (!Arrays.equals(token, verifyToken)) {
            throw new IllegalStateException("Invalid verify token");
        }
    }

    private void beginBackendConnect(ChannelHandlerContext ctx, String preferredServer) {
        phase = Phase.CONNECTING_BACKEND;
        BackendMonitor mon = server.backendMonitor();
        String pick = preferredServer != null ? preferredServer : forcedServerName;
        LinkConfig.Backend target = mon.pickLoginTarget(pick);
        RegisteredServer reg = server.plugins().proxy().server(target.name()).orElse(null);
        if (reg == null && target != null) {
            reg = new RegisteredServerImpl(server, target);
        }
        InetSocketAddress addr = clientCtx != null && clientCtx.channel().remoteAddress() instanceof InetSocketAddress isa
                ? isa : new InetSocketAddress(clientAddress, 0);
        PreConnectEvent pre = new PreConnectEvent(playerId, username, addr, reg);
        server.plugins().eventBus().fire(pre);
        if (pre.isCancelled()) {
            kick(ctx, pre.denyReason() != null ? pre.denyReason() : "Connection denied");
            return;
        }
        if (pre.target() != null) {
            LinkConfig.Backend chosen = server.config().findServer(pre.target().name());
            if (chosen != null) {
                target = chosen;
            }
        }
        connectBackend(ctx, target);
    }

    private void connectBackend(ChannelHandlerContext clientCtx, LinkConfig.Backend target) {
        currentBackendName = target.name();
        LOG.info("CONNECT user=" + username + " → " + target.name()
                + " (" + target.host() + ":" + target.port() + ") proto=" + protocolVersion
                + (virtualHost.isBlank() ? "" : " vhost=" + virtualHost));

        Bootstrap b = new Bootstrap();
        b.group(clientCtx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        server.config().connectTimeoutMs())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("frame-dec", new McFrameCodec.Decoder())
                                .addLast("frame-enc", new McFrameCodec.Encoder())
                                .addLast("backend", new BackendLoginHandler(clientCtx.channel()));
                    }
                });

        b.connect(target.host(), target.port()).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                LOG.warning("BACKEND fail " + target.name() + ": " + f.cause());
                kick(clientCtx, "Could not connect to backend " + target.name());
                return;
            }
            backend = f.channel();
            sendBackendHandshake(target);
            sendBackendLoginStart();
        });
    }

    private void sendBackendHandshake(LinkConfig.Backend target) {
        ByteBuf hs = Unpooled.buffer();
        McCodec.writeVarInt(hs, 0x00);
        McCodec.writeVarInt(hs, protocolVersion);
        String hostField = target.host();
        if (floodgatePayload != null && server.floodgate().enabled()) {
            hostField = server.floodgate().forwardingHostname(hostField, floodgatePayload);
        }
        McCodec.writeString(hs, hostField);
        hs.writeShort(target.port());
        McCodec.writeVarInt(hs, 2);
        backend.writeAndFlush(hs);
    }

    private void sendBackendLoginStart() {
        ByteBuf login = Unpooled.buffer();
        McCodec.writeVarInt(login, 0x00);
        McCodec.writeString(login, username);
        McCodec.writeUuid(login, playerId);
        backend.writeAndFlush(login);
    }

    private final class BackendLoginHandler extends ChannelInboundHandlerAdapter {
        private final Channel client;
        private boolean forwarded;
        private McCompressionCodec.Decoder clientCompDec;
        private McCompressionCodec.Encoder clientCompEnc;
        private McCompressionCodec.Decoder backendCompDec;
        private McCompressionCodec.Encoder backendCompEnc;

        BackendLoginHandler(Channel client) {
            this.client = client;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) {
                return;
            }
            buf.markReaderIndex();
            int packetId = McCodec.readVarInt(buf);
            try {
                if (packetId == 0x00) {
                    buf.resetReaderIndex();
                    client.writeAndFlush(buf.retain()).addListener(ChannelFutureListener.CLOSE);
                    ctx.close();
                    return;
                }
                if (packetId == 0x03) {
                    int threshold = McCodec.readVarInt(buf);
                    buf.release();
                    enableCompression(client, ctx.channel(), threshold);
                    forwardSetCompression(threshold);
                    return;
                }
                if (packetId == 0x04) {
                    handlePluginRequest(ctx, buf);
                    return;
                }
                if (packetId == 0x02) {
                    if (!forwarded) {
                        buf.release();
                        kickChannel(client, "Backend did not request modern forwarding");
                        ctx.close();
                        return;
                    }
                    buf.resetReaderIndex();
                    client.writeAndFlush(buf.retain());
                    beginBridge(client, ctx.channel());
                    return;
                }
                buf.release();
                kickChannel(client, "Unexpected backend login packet 0x" + Integer.toHexString(packetId));
                ctx.close();
            } catch (Exception e) {
                buf.release();
                LOG.log(Level.WARNING, "backend login failed", e);
                kickChannel(client, "Backend login error");
                ctx.close();
            }
        }

        private void handlePluginRequest(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
            int messageId = McCodec.readVarInt(buf);
            String channel = McCodec.readString(buf, 32767);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            buf.release();
            if (ModernForwarding.CHANNEL.equals(channel)) {
                ByteBuf payload = ModernForwarding.createForwardingData(
                        server.config().forwardingSecret(),
                        clientAddress,
                        playerId,
                        username,
                        properties
                );
                ByteBuf resp = Unpooled.buffer();
                McCodec.writeVarInt(resp, 0x02);
                McCodec.writeVarInt(resp, messageId);
                resp.writeBoolean(true);
                resp.writeBytes(payload);
                payload.release();
                ctx.writeAndFlush(resp);
                forwarded = true;
            } else {
                ByteBuf resp = Unpooled.buffer();
                McCodec.writeVarInt(resp, 0x02);
                McCodec.writeVarInt(resp, messageId);
                resp.writeBoolean(false);
                ctx.writeAndFlush(resp);
            }
        }

        private void forwardSetCompression(int threshold) {
            ByteBuf fwd = Unpooled.buffer();
            McCodec.writeVarInt(fwd, 0x03);
            McCodec.writeVarInt(fwd, threshold);
            client.writeAndFlush(fwd);
        }

        private void enableCompression(Channel clientCh, Channel backendCh, int threshold) {
            if (threshold < 0) {
                return;
            }
            if (backendCompDec == null) {
                backendCompDec = new McCompressionCodec.Decoder();
                backendCompEnc = new McCompressionCodec.Encoder();
                backendCh.pipeline().addAfter("frame-dec", "comp-dec", backendCompDec);
                backendCh.pipeline().addAfter("frame-enc", "comp-enc", backendCompEnc);
            }
            backendCompDec.setThreshold(threshold);
            backendCompEnc.setThreshold(threshold);

            if (clientCompDec == null) {
                clientCompDec = new McCompressionCodec.Decoder();
                clientCompEnc = new McCompressionCodec.Encoder();
                clientCh.pipeline().addAfter("frame-dec", "comp-dec", clientCompDec);
                clientCh.pipeline().addBefore("frame-enc", "comp-enc", clientCompEnc);
            }
            clientCompDec.setThreshold(threshold);
            clientCompEnc.setThreshold(threshold);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
            client.close();
        }
    }

    private void beginBridge(Channel client, Channel backendCh) {
        phase = Phase.BRIDGING;
        if (!counted) {
            counted = true;
            server.playerJoined();
        }
        registerPlayerHub(client);
        initPlayerHandle(client);
        RegisteredServer reg = server.plugins().proxy().server(currentBackendName).orElse(null);
        if (reg != null && playerHandle != null) {
            server.plugins().eventBus().fire(new PostConnectEvent(playerHandle, reg));
        }
        server.chatRelay().announceJoin(username, currentBackendName);
        if (server.config().globalTabList()) {
            sendNetworkTabHint(client);
        }
        LOG.info("BRIDGE user=" + username + " backend=" + currentBackendName + " uuid=" + playerId);

        client.pipeline().replace("client", "to-backend",
                new PlayRelay(backendCh, true));
        backendCh.pipeline().replace("backend", "to-client",
                new PlayRelay(client, false));
    }

    private void registerPlayerHub(Channel client) {
        PlayerHub.PlayerRecord record = new PlayerHub.PlayerRecord(
                playerId,
                username,
                currentBackendName,
                protocolVersion,
                json -> sendPlaySystemChat(client, json)
        );
        server.playerHub().join(record);
    }

    private void initPlayerHandle(Channel client) {
        InetSocketAddress addr = client.remoteAddress() instanceof InetSocketAddress isa
                ? isa : new InetSocketAddress(clientAddress, 0);
        playerHandle = new LinkPlayerImpl(this, playerId, username, addr);
        playerHandle.grantPermission("yaplink.server");
        server.registerSession(playerId, this);
    }

    public LinkPlayerImpl playerHandle() {
        return playerHandle;
    }

    public String backendName() {
        return currentBackendName;
    }

    public Optional<RegisteredServer> currentServer() {
        if (currentBackendName == null) {
            return Optional.empty();
        }
        return server.plugins().proxy().server(currentBackendName);
    }

    public void sendSystemMessage(String text) {
        Channel client = clientCtx != null ? clientCtx.channel() : null;
        sendPlaySystemChat(client, PlayChat.jsonText(text));
    }

    public void kick(String reason) {
        kickChannel(clientCtx != null ? clientCtx.channel() : null, reason);
    }

    public void switchServer(String serverName) {
        handleServerCommand(serverName);
    }

    public void sendBackendPluginMessage(ChannelIdentifier channel, byte[] data) {
        if (backend == null || !backend.isActive() || channel == null || data == null) {
            return;
        }
        ByteBuf buf = Unpooled.buffer();
        PluginMessagePackets.writeServerbound(buf, protocolVersion, channel.id(), data);
        backend.writeAndFlush(buf);
    }

    private void sendNetworkTabHint(Channel client) {
        StringBuilder sb = new StringBuilder("Online on network: ");
        server.playerHub().all().forEach(p ->
                sb.append(p.username()).append('@').append(p.backendName()).append(' '));
        sendPlaySystemChat(client, PlayChat.jsonText(sb.toString().trim()));
    }

    private void sendPlaySystemChat(Channel client, String jsonComponent) {
        if (client == null || !client.isActive()) {
            return;
        }
        ByteBuf pkt = PlayChat.systemChatPacket(protocolVersion, jsonComponent, false);
        client.writeAndFlush(pkt);
    }

    private final class PlayRelay extends ChannelInboundHandlerAdapter {
        private final Channel peer;
        private final boolean fromClient;

        PlayRelay(Channel peer, boolean fromClient) {
            this.peer = peer;
            this.fromClient = fromClient;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) {
                return;
            }
            if (fromClient) {
                if (server.config().enableServerCommand()) {
                    String cmd = extractServerCommand(buf);
                    if (cmd != null) {
                        buf.release();
                        handleServerCommand(cmd);
                        return;
                    }
                }
                server.chatRelay().tryRelayClientPacket(
                        protocolVersion, playerId, username, currentBackendName, buf);
                tryFirePluginMessage(buf, true);
            } else {
                tryFirePluginMessage(buf, false);
            }
            if (peer.isActive()) {
                peer.writeAndFlush(msg);
            } else {
                buf.release();
                ctx.close();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            peer.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
            peer.close();
        }
    }

    private void tryFirePluginMessage(ByteBuf buf, boolean fromClient) {
        if (!server.config().pluginsEnabled()) {
            return;
        }
        if (server.plugins().registeredChannelIds().isEmpty()) {
            return;
        }
        Optional<PluginMessagePackets.Parsed> parsed = fromClient
                ? PluginMessagePackets.tryParseServerbound(protocolVersion, buf)
                : PluginMessagePackets.tryParseClientbound(protocolVersion, buf);
        if (parsed.isEmpty()) {
            return;
        }
        String channelId = parsed.get().channel();
        if (!server.plugins().isRegisteredChannel(channelId)) {
            return;
        }
        ChannelIdentifier channel = ChannelIdentifier.fromMcChannel(channelId);
        PluginMessageEvent event = new PluginMessageEvent(
                fromClient ? PluginMessageEvent.SourceKind.PLAYER : PluginMessageEvent.SourceKind.BACKEND,
                fromClient ? Optional.ofNullable(playerHandle) : Optional.empty(),
                fromClient ? Optional.empty() : currentServer(),
                channel,
                parsed.get().data()
        );
        server.plugins().eventBus().fire(event);
        server.metrics().counter("plugin.messages", 1);
    }

    private static String extractServerCommand(ByteBuf buf) {
        buf.markReaderIndex();
        try {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            String s = new String(bytes, StandardCharsets.UTF_8);
            int idx = indexOfIgnoreCase(s, "server ");
            if (idx < 0) {
                return null;
            }
            if (idx > 0) {
                char c = s.charAt(idx - 1);
                if (Character.isLetterOrDigit(c)) {
                    return null;
                }
            }
            String rest = s.substring(idx + "server ".length()).trim();
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (Character.isWhitespace(c) || c == '\0' || c == '"') {
                    break;
                }
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                    name.append(c);
                } else {
                    break;
                }
            }
            return name.length() == 0 ? null : name.toString();
        } finally {
            buf.resetReaderIndex();
        }
    }

    private static int indexOfIgnoreCase(String hay, String needle) {
        return hay.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private void handleServerCommand(String serverName) {
        LinkConfig.Backend target = server.config().findServer(serverName);
        Channel client = clientCtx != null ? clientCtx.channel() : null;
        if (target == null) {
            sendPlaySystemChat(client, PlayChat.jsonText("Unknown server: " + serverName
                    + " — known: " + String.join(", ", server.config().servers().keySet())));
            return;
        }
        if (!server.backendMonitor().isUp(target.name())) {
            sendPlaySystemChat(client, PlayChat.jsonText("Server " + target.name() + " is currently unavailable."));
            return;
        }
        if (target.name().equalsIgnoreCase(currentBackendName)) {
            sendPlaySystemChat(client, PlayChat.jsonText("Already connected to " + target.name()));
            return;
        }
        RegisteredServer reg = server.plugins().proxy().server(target.name()).orElse(null);
        if (reg != null && playerHandle != null) {
            ServerChooseEvent choose = new ServerChooseEvent(playerHandle, reg);
            server.plugins().eventBus().fire(choose);
            if (choose.isCancelled()) {
                return;
            }
            if (choose.target() != null) {
                LinkConfig.Backend redirected = server.config().findServer(choose.target().name());
                if (redirected != null) {
                    target = redirected;
                }
            }
        }
        LOG.info("SERVER user=" + username + " → " + target.name());
        server.redirects().put(playerId, target.name());
        if (protocolVersion >= 766 && client != null && client.isActive()) {
            ByteBuf transfer = Unpooled.buffer();
            McCodec.writeVarInt(transfer, transferPacketId(protocolVersion));
            McCodec.writeString(transfer, server.config().publicHost());
            McCodec.writeVarInt(transfer, server.config().publicPort());
            client.writeAndFlush(transfer).addListener(f -> {
                if (backend != null) {
                    backend.close();
                }
            });
            return;
        }
        if (backend != null) {
            backend.close();
        }
        kickChannel(client, "Sending you to " + target.name() + " — reconnect to YaP Link.");
    }

    private static int transferPacketId(int protocol) {
        return protocol >= 768 ? 0x7A : 0x73;
    }

    private void kick(ChannelHandlerContext ctx, String reason) {
        if (ctx != null) {
            kickChannel(ctx.channel(), reason);
        }
    }

    private void kickChannel(Channel ch, String reason) {
        if (ch == null || !ch.isActive()) {
            return;
        }
        try {
            if (phase == Phase.BRIDGING) {
                ch.writeAndFlush(PlayChat.disconnectPacket(protocolVersion, PlayChat.jsonText(reason)))
                        .addListener(ChannelFutureListener.CLOSE);
            } else {
                JsonObject chat = new JsonObject();
                chat.addProperty("text", reason);
                ByteBuf buf = Unpooled.buffer();
                McCodec.writeVarInt(buf, 0x00);
                McCodec.writeString(buf, GSON.toJson(chat));
                ch.writeAndFlush(buf).addListener(ChannelFutureListener.CLOSE);
            }
        } catch (Exception e) {
            ch.close();
        }
    }

    private void teardown(ChannelHandlerContext ctx) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (playerId != null) {
            server.playerHub().leave(playerId);
            server.unregisterSession(playerId);
            if (playerHandle != null) {
                server.plugins().eventBus().fire(new DisconnectEvent(playerHandle));
            }
        }
        if (username != null && currentBackendName != null) {
            server.chatRelay().announceLeave(username, currentBackendName);
            LOG.info("DISCONNECT user=" + username + " backend=" + currentBackendName);
        }
        if (counted) {
            server.playerLeft();
            counted = false;
        }
        if (backend != null) {
            backend.close();
            backend = null;
        }
        ctx.close();
    }
}

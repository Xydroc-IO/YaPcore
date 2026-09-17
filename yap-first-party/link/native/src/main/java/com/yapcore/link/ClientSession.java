package com.yapcore.link;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.yapcore.link.floodgate.FloodgateForwarder;
import com.yapcore.link.plugin.LinkPlayerImpl;
import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.event.DisconnectEvent;
import com.yapcore.link.api.event.PostConnectEvent;
import com.yapcore.link.forwarding.ModernForwarding;
import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.protocol.PlayChat;
import com.yapcore.link.protocol.PluginMessagePackets;
import com.yapcore.link.session.PlayerHub;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutException;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.List;
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

    enum Phase {
        HANDSHAKE, STATUS, LOGIN_CLIENT, LOGIN_ENCRYPT, CONNECTING_BACKEND, BRIDGING
    }

    final LinkServer server;
    Phase phase = Phase.HANDSHAKE;
    int protocolVersion;
    String username;
    UUID playerId;
    List<ModernForwarding.Property> properties = List.of();
    String clientAddress = "127.0.0.1";
    String virtualHost = "";
    /** TCP port from the client's handshake (same port they used to reach Link). */
    int virtualPort;
    String forcedServerName;
    Channel backend;
    String currentBackendName;
    final AtomicBoolean closed = new AtomicBoolean(false);
    /** True while an in-proxy soft backend swap is in progress. */
    final AtomicBoolean switching = new AtomicBoolean(false);
    /** Client has acknowledged {@code start_configuration} during a soft switch. */
    volatile boolean clientInConfig;
    String pendingSwitchTarget;
    boolean counted;
    final byte[] verifyToken = new byte[4];
    ChannelHandlerContext clientCtx;
    long loginStartedMs;
    LinkPlayerImpl playerHandle;
    String floodgatePayload;

    private final ClientSessionStatusPing statusPing = new ClientSessionStatusPing(this);
    private final ClientSessionLoginFlow loginFlow = new ClientSessionLoginFlow(this);

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
                case STATUS -> statusPing.handleStatus(ctx, buf);
                case LOGIN_CLIENT -> loginFlow.handleLoginStart(ctx, buf);
                case LOGIN_ENCRYPT -> loginFlow.handleEncryptionResponse(ctx, buf);
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
        LinkConfig cfg = server.config();
        if (!server.rateGuard().allowHandshake(clientAddress, cfg)) {
            buf.release();
            ctx.close();
            return;
        }
        int packetId = McCodec.readVarInt(buf);
        if (packetId != 0x00) {
            buf.release();
            ctx.close();
            return;
        }
        protocolVersion = McCodec.readVarInt(buf);
        virtualHost = McCodec.readString(buf, 255);
        virtualPort = buf.readUnsignedShort();
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
            if (!server.rateGuard().allowLogin(clientAddress, cfg)) {
                ctx.close();
                return;
            }
            phase = Phase.LOGIN_CLIENT;
        } else {
            ctx.close();
        }
    }

    void beginBridge(Channel client, Channel backendCh) {
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
                new ClientSessionPlayRelay(this, backendCh, true));
        backendCh.pipeline().replace("backend", "to-client",
                new ClientSessionPlayRelay(this, client, false));
        // Do NOT send minecraft:register here — beginBridge runs at Login Success, before
        // configuration/play. Play packet id 22 during config kicks with "unknown packet id 22".
        // PlayRelay calls ensureProxyChannelsRegistered() on the first play packet instead.
    }

    /**
     * After a soft switch finishes configuration, re-attach play relays on the existing client.
     */
    void rebridgeAfterSwitch(Channel client, Channel backendCh) {
        if (client == null || backendCh == null || !client.isActive() || !backendCh.isActive()) {
            kickChannel(client, "Switch failed — backend lost");
            return;
        }
        phase = Phase.BRIDGING;
        if (playerHandle != null) {
            server.playerHub().leave(playerId);
            registerPlayerHub(client);
        }
        RegisteredServer reg = server.plugins().proxy().server(currentBackendName).orElse(null);
        if (reg != null && playerHandle != null) {
            server.plugins().eventBus().fire(new PostConnectEvent(playerHandle, reg));
        }
        server.chatRelay().announceJoin(username, currentBackendName);
        LOG.info("BRIDGE (switch) user=" + username + " backend=" + currentBackendName + " uuid=" + playerId);

        String clientHandler = client.pipeline().get("switch-client") != null
                ? "switch-client"
                : (client.pipeline().get("to-backend") != null ? "to-backend" : "client");
        client.pipeline().replace(clientHandler, "to-backend",
                new ClientSessionPlayRelay(this, backendCh, true));

        String backendHandler = backendCh.pipeline().get("backend") != null
                ? "backend"
                : (backendCh.pipeline().get("to-client") != null ? "to-client" : "backend");
        backendCh.pipeline().replace(backendHandler, "to-client",
                new ClientSessionPlayRelay(this, client, false));
    }

    private final AtomicBoolean proxyChannelsRegistered = new AtomicBoolean(false);

    void resetProxyChannelsRegistered() {
        proxyChannelsRegistered.set(false);
    }

    /**
     * Once the backend is in play, send serverbound {@code minecraft:register} so Folia/Paper
     * populates CraftPlayer.channels() and BungeeCord {@code Connect} actually hits the wire.
     * Channel names must be valid lowercase Identifiers or Folia kicks with
     * {@code Invalid custom payload payload!}.
     */
    void ensureProxyChannelsRegistered() {
        if (!proxyChannelsRegistered.compareAndSet(false, true)) {
            return;
        }
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        names.add("bungeecord:main");
        for (String id : server.plugins().registeredChannelIds()) {
            String normalized = normalizeRegisterChannel(id);
            if (normalized != null) {
                names.add(normalized);
            }
        }
        StringBuilder joined = new StringBuilder();
        for (String name : names) {
            if (joined.length() > 0) {
                joined.append('\0');
            }
            joined.append(name);
        }
        byte[] payload = joined.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        sendBackendPluginMessage(ChannelIdentifier.fromMcChannel("minecraft:register"), payload);
        LOG.info("REGISTER channels → backend user=" + username + " count=" + names.size()
                + " " + names);
    }

    /** Paper requires entirely lowercase {@code namespace:key} channel ids. */
    private static String normalizeRegisterChannel(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String s = id.trim();
        if ("BungeeCord".equalsIgnoreCase(s) || "minecraft:bungeecord".equalsIgnoreCase(s)) {
            return "bungeecord:main";
        }
        s = s.toLowerCase(java.util.Locale.ROOT);
        int colon = s.indexOf(':');
        if (colon <= 0 || colon >= s.length() - 1) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ':') {
                continue;
            }
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')
                    && c != '_' && c != '-' && c != '.' && c != '/') {
                return null;
            }
        }
        return s;
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
        ClientSessionRouting.handleServerCommand(this, serverName);
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

    void sendPlaySystemChat(Channel client, String jsonComponent) {
        if (client == null || !client.isActive()) {
            return;
        }
        ByteBuf pkt = PlayChat.systemChatPacket(protocolVersion, jsonComponent, false);
        client.writeAndFlush(pkt);
    }

    void kick(ChannelHandlerContext ctx, String reason) {
        if (ctx != null) {
            kickChannel(ctx.channel(), reason);
        }
    }

    void kickChannel(Channel ch, String reason) {
        if (ch == null || !ch.isActive()) {
            return;
        }
        try {
            // Once the client has reached play (or is mid soft-switch), always use play disconnect.
            // Login-format id 0 + JSON is decoded as play bundle_delimiter → client crash.
            boolean playClient = phase == Phase.BRIDGING
                    || switching.get()
                    || clientInConfig
                    || (ch.pipeline().get("to-backend") != null)
                    || (ch.pipeline().get("switch-client") != null);
            if (playClient) {
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

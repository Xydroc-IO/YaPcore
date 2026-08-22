package com.yapcore.link;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yapcore.link.auth.MojangAuth;
import com.yapcore.link.crypto.MinecraftCrypto;
import com.yapcore.link.forwarding.ModernForwarding;
import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.protocol.McCompressionCodec;
import com.yapcore.link.protocol.McFrameCodec;
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

import javax.crypto.Cipher;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client connection: status / login (offline or Mojang) → modern-forward backend → bridge.
 * Supports compression, {@code /server}, and Transfer reconnect tokens.
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
    private Channel backend;
    private String currentBackendName;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private boolean counted;
    private final byte[] verifyToken = new byte[4];
    private ChannelHandlerContext clientCtx;

    public ClientSession(LinkServer server) {
        this.server = server;
        new SecureRandom().nextBytes(verifyToken);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.clientCtx = ctx;
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
        LOG.log(Level.FINE, "client error: " + cause.getMessage(), cause);
        teardown(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf buf)) {
            return;
        }
        try {
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

    private void handleHandshake(ChannelHandlerContext ctx, ByteBuf buf) {
        int packetId = McCodec.readVarInt(buf);
        if (packetId != 0x00) {
            buf.release();
            ctx.close();
            return;
        }
        protocolVersion = McCodec.readVarInt(buf);
        McCodec.readString(buf, 255);
        buf.readUnsignedShort();
        int intent = McCodec.readVarInt(buf);
        buf.release();
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
        JsonObject root = new JsonObject();
        JsonObject version = new JsonObject();
        version.addProperty("name", "YaP Link");
        version.addProperty("protocol", protocolVersion > 0 ? protocolVersion : 776);
        root.add("version", version);
        JsonObject players = new JsonObject();
        players.addProperty("max", server.config().maxPlayers());
        players.addProperty("online", server.onlinePlayers());
        players.add("sample", new JsonArray());
        root.add("players", players);
        JsonObject desc = new JsonObject();
        desc.addProperty("text", server.config().motd());
        root.add("description", desc);
        return GSON.toJson(root);
    }

    private void handleLoginStart(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        int packetId = McCodec.readVarInt(buf);
        if (packetId != 0x00) {
            buf.release();
            kick(ctx, "Unexpected login packet");
            return;
        }
        username = McCodec.readString(buf, 16);
        if (buf.readableBytes() >= 16) {
            try {
                playerId = McCodec.readUuid(buf);
            } catch (Exception e) {
                playerId = McCodec.offlineUuid(username);
            }
        } else {
            playerId = McCodec.offlineUuid(username);
        }
        buf.release();

        if (server.config().onlineMode()) {
            phase = Phase.LOGIN_ENCRYPT;
            ByteBuf enc = Unpooled.buffer();
            McCodec.writeVarInt(enc, 0x01); // Encryption Request
            McCodec.writeString(enc, ""); // server id
            byte[] pub = server.rsaKeyPair().getPublic().getEncoded();
            McCodec.writeVarInt(enc, pub.length);
            enc.writeBytes(pub);
            McCodec.writeVarInt(enc, verifyToken.length);
            enc.writeBytes(verifyToken);
            if (protocolVersion >= 766) {
                enc.writeBoolean(true); // shouldAuthenticate
            }
            ctx.writeAndFlush(enc);
            return;
        }

        String redirect = server.redirects().take(playerId);
        beginBackendConnect(ctx, redirect);
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
            // 1.20.5+: boolean hasVerifyToken; if true → token, else chat salt/signature (ignore)
            boolean hasVerifyToken = buf.readBoolean();
            if (hasVerifyToken) {
                int tokenLen = McCodec.readVarInt(buf);
                byte[] tokenEnc = new byte[tokenLen];
                buf.readBytes(tokenEnc);
                byte[] token = MinecraftCrypto.decryptRsa(server.rsaKeyPair(), tokenEnc);
                if (!Arrays.equals(token, verifyToken)) {
                    buf.release();
                    kick(ctx, "Invalid verify token");
                    return;
                }
            } else {
                buf.readLong(); // salt
                int sigLen = McCodec.readVarInt(buf);
                buf.skipBytes(Math.min(sigLen, buf.readableBytes()));
            }
        } else {
            int tokenLen = McCodec.readVarInt(buf);
            byte[] tokenEnc = new byte[tokenLen];
            buf.readBytes(tokenEnc);
            byte[] token = MinecraftCrypto.decryptRsa(server.rsaKeyPair(), tokenEnc);
            if (!Arrays.equals(token, verifyToken)) {
                buf.release();
                kick(ctx, "Invalid verify token");
                return;
            }
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
        LOG.info("Online-mode OK " + username + " " + playerId);

        String redirect = server.redirects().take(playerId);
        beginBackendConnect(ctx, redirect);
    }

    private void beginBackendConnect(ChannelHandlerContext ctx, String preferredServer) {
        phase = Phase.CONNECTING_BACKEND;
        LinkConfig.Backend target = preferredServer != null
                ? server.config().findServer(preferredServer)
                : null;
        if (target == null) {
            target = server.config().resolveTry();
        }
        connectBackend(ctx, target);
    }

    private void connectBackend(ChannelHandlerContext clientCtx, LinkConfig.Backend target) {
        currentBackendName = target.name();
        LOG.info("Connecting " + username + " → " + target.name()
                + " (" + target.host() + ":" + target.port() + ") protocol=" + protocolVersion);

        Bootstrap b = new Bootstrap();
        b.group(clientCtx.channel().eventLoop())
                .channel(NioSocketChannel.class)
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
                LOG.warning("Backend connect failed: " + f.cause());
                kick(clientCtx, "Could not connect to backend " + target.name());
                return;
            }
            backend = f.channel();
            ByteBuf hs = Unpooled.buffer();
            McCodec.writeVarInt(hs, 0x00);
            McCodec.writeVarInt(hs, protocolVersion);
            McCodec.writeString(hs, target.host());
            hs.writeShort(target.port());
            McCodec.writeVarInt(hs, 2);
            backend.writeAndFlush(hs);

            ByteBuf login = Unpooled.buffer();
            McCodec.writeVarInt(login, 0x00);
            McCodec.writeString(login, username);
            McCodec.writeUuid(login, playerId);
            backend.writeAndFlush(login);
        });
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
                    // Forward Set Compression to client
                    ByteBuf fwd = Unpooled.buffer();
                    McCodec.writeVarInt(fwd, 0x03);
                    McCodec.writeVarInt(fwd, threshold);
                    client.writeAndFlush(fwd);
                    return;
                }
                if (packetId == 0x04) {
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
                // frame-dec → comp-dec → handler; handler → comp-enc → frame-enc
                clientCh.pipeline().addAfter("frame-dec", "comp-dec", clientCompDec);
                clientCh.pipeline().addBefore("frame-enc", "comp-enc", clientCompEnc);
            }
            clientCompDec.setThreshold(threshold);
            clientCompEnc.setThreshold(threshold);
            LOG.fine("Compression enabled threshold=" + threshold);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.log(Level.FINE, "backend error", cause);
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
        client.pipeline().replace("client", "to-backend",
                new PlayRelay(backendCh, true));
        backendCh.pipeline().replace("backend", "to-client",
                new PlayRelay(client, false));
        LOG.info("Bridged " + username + " (" + playerId + ") on " + currentBackendName);
    }

    /** Relays play/config packets; client→backend side may intercept /server. */
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
            if (fromClient && server.config().enableServerCommand()) {
                String cmd = extractServerCommand(buf);
                if (cmd != null) {
                    buf.release();
                    handleServerCommand(cmd);
                    return;
                }
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

    /**
     * Best-effort: find {@code server <name>} or {@code /server <name>} in packet body
     * (works for unsigned chat / command packets).
     */
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
            // require leading / or start-ish (command packet often omits /)
            if (idx > 0) {
                char c = s.charAt(idx - 1);
                if (c != '/' && c != '\0' && !Character.isISOControl(c) && c != '"') {
                    // allow if previous is non-letter
                    if (Character.isLetterOrDigit(c)) {
                        return null;
                    }
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
        return hay.toLowerCase().indexOf(needle.toLowerCase());
    }

    private void handleServerCommand(String serverName) {
        LinkConfig.Backend target = server.config().findServer(serverName);
        Channel client = clientCtx != null ? clientCtx.channel() : null;
        if (target == null) {
            sendSystemMessage(client, "Unknown server: " + serverName
                    + " — known: " + String.join(", ", server.config().servers().keySet()));
            return;
        }
        if (target.name().equalsIgnoreCase(currentBackendName)) {
            sendSystemMessage(client, "Already connected to " + target.name());
            return;
        }
        LOG.info("/server " + target.name() + " for " + username);
        server.redirects().put(playerId, target.name());
        // Prefer Transfer (1.20.5+) so the client reconnects to Link and lands on the token.
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
        kickChannel(client, "Sending you to " + target.name()
                + " — reconnect to YaP Link to finish transfer.");
    }

    /** Play clientbound Transfer packet id by protocol (best-effort). */
    private static int transferPacketId(int protocol) {
        if (protocol >= 768) {
            return 0x7A;
        }
        return 0x73; // 1.20.5-ish fallback
    }

    private void sendSystemMessage(Channel client, String text) {
        if (client == null || !client.isActive()) {
            return;
        }
        // system chat is protocol-sensitive; use disconnect-style only as last resort log
        LOG.info("[msg→" + username + "] " + text);
        // Login-phase kick packet won't work in play — try generic disconnect play 0x1D varies.
        // Prefer in-game: skip for now; Transfer/kick paths cover /server UX.
    }

    private void kickChannel(Channel ch, String reason) {
        if (ch == null || !ch.isActive()) {
            return;
        }
        try {
            JsonObject chat = new JsonObject();
            chat.addProperty("text", reason);
            ByteBuf buf = Unpooled.buffer();
            McCodec.writeVarInt(buf, 0x00);
            McCodec.writeString(buf, GSON.toJson(chat));
            ch.writeAndFlush(buf).addListener(ChannelFutureListener.CLOSE);
        } catch (Exception e) {
            ch.close();
        }
    }

    private void kick(ChannelHandlerContext ctx, String reason) {
        if (ctx != null) {
            kickChannel(ctx.channel(), reason);
        }
    }

    private void teardown(ChannelHandlerContext ctx) {
        if (!closed.compareAndSet(false, true)) {
            return;
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

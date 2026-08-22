package com.yapcore.link;

import com.yapcore.link.auth.MojangAuth;
import com.yapcore.link.backend.BackendMonitor;
import com.yapcore.link.crypto.MinecraftCrypto;
import com.yapcore.link.floodgate.FloodgateForwarder;
import com.yapcore.link.plugin.RegisteredServerImpl;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.event.LoginEvent;
import com.yapcore.link.api.event.PreConnectEvent;
import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.protocol.McFrameCodec;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import javax.crypto.Cipher;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Login handshake, encryption, and backend connection bootstrap. */
final class ClientSessionLoginFlow {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Client");

    private final ClientSession session;

    ClientSessionLoginFlow(ClientSession session) {
        this.session = session;
    }

    void handleLoginStart(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        int packetId = McCodec.readVarInt(buf);
        if (packetId != 0x00) {
            buf.release();
            session.kick(ctx, "Unexpected login packet");
            return;
        }
        session.username = McCodec.readString(buf, 16);
        session.playerId = readPlayerId(buf);
        buf.release();

        applyFloodgateIdentity();

        InetSocketAddress addr = session.clientCtx != null && session.clientCtx.channel().remoteAddress() instanceof InetSocketAddress isa
                ? isa : new InetSocketAddress(session.clientAddress, 0);
        LoginEvent login = new LoginEvent(session.playerId, session.username, addr);
        session.server.plugins().eventBus().fire(login);
        if (login.isCancelled()) {
            session.kick(ctx, login.denyReason() != null ? login.denyReason() : "Login denied");
            return;
        }

        if (session.server.config().onlineMode()) {
            session.phase = ClientSession.Phase.LOGIN_ENCRYPT;
            sendEncryptionRequest(ctx);
            return;
        }

        String redirect = session.server.redirects().take(session.playerId);
        beginBackendConnect(ctx, redirect);
    }

    void handleEncryptionResponse(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        int packetId = McCodec.readVarInt(buf);
        if (packetId != 0x01) {
            buf.release();
            session.kick(ctx, "Expected encryption response");
            return;
        }
        int secretLen = McCodec.readVarInt(buf);
        byte[] secretEnc = new byte[secretLen];
        buf.readBytes(secretEnc);
        byte[] sharedSecret = MinecraftCrypto.decryptRsa(session.server.rsaKeyPair(), secretEnc);

        if (session.protocolVersion >= 766 && buf.isReadable()) {
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

        String serverId = MinecraftCrypto.serverId(sharedSecret, session.server.rsaKeyPair().getPublic());
        MojangAuth.Profile profile = MojangAuth.hasJoined(session.username, serverId);
        session.playerId = profile.id();
        session.username = profile.name();
        session.properties = profile.properties();
        LOG.info("AUTH ok user=" + session.username + " uuid=" + session.playerId + " addr=" + session.clientAddress);

        applyFloodgateIdentity();
        beginBackendConnect(ctx, session.server.redirects().take(session.playerId));
    }

    void beginBackendConnect(ChannelHandlerContext ctx, String preferredServer) {
        session.phase = ClientSession.Phase.CONNECTING_BACKEND;
        BackendMonitor mon = session.server.backendMonitor();
        String pick = preferredServer != null ? preferredServer : session.forcedServerName;
        LinkConfig.Backend target = mon.pickLoginTarget(pick);
        RegisteredServer reg = session.server.plugins().proxy().server(target.name()).orElse(null);
        if (reg == null && target != null) {
            reg = new RegisteredServerImpl(session.server, target);
        }
        InetSocketAddress addr = session.clientCtx != null && session.clientCtx.channel().remoteAddress() instanceof InetSocketAddress isa
                ? isa : new InetSocketAddress(session.clientAddress, 0);
        PreConnectEvent pre = new PreConnectEvent(session.playerId, session.username, addr, reg);
        session.server.plugins().eventBus().fire(pre);
        if (pre.isCancelled()) {
            session.kick(ctx, pre.denyReason() != null ? pre.denyReason() : "Connection denied");
            return;
        }
        if (pre.target() != null) {
            LinkConfig.Backend chosen = session.server.config().findServer(pre.target().name());
            if (chosen != null) {
                target = chosen;
            }
        }
        connectBackend(ctx, target);
    }

    void connectBackend(ChannelHandlerContext clientCtx, LinkConfig.Backend target) {
        session.currentBackendName = target.name();
        LOG.info("CONNECT user=" + session.username + " → " + target.name()
                + " (" + target.host() + ":" + target.port() + ") proto=" + session.protocolVersion
                + (session.virtualHost.isBlank() ? "" : " vhost=" + session.virtualHost));

        Bootstrap b = new Bootstrap();
        b.group(clientCtx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        session.server.config().connectTimeoutMs())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("frame-dec", new McFrameCodec.Decoder())
                                .addLast("frame-enc", new McFrameCodec.Encoder())
                                .addLast("backend", new ClientSessionBackendLoginHandler(session, clientCtx.channel()));
                    }
                });

        b.connect(target.host(), target.port()).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                LOG.warning("BACKEND fail " + target.name() + ": " + f.cause());
                session.kick(clientCtx, "Could not connect to backend " + target.name());
                return;
            }
            session.backend = f.channel();
            sendBackendHandshake(target);
            sendBackendLoginStart();
        });
    }

    private UUID readPlayerId(ByteBuf buf) {
        if (buf.readableBytes() >= 16) {
            try {
                return McCodec.readUuid(buf);
            } catch (Exception e) {
                return McCodec.offlineUuid(session.username);
            }
        }
        return McCodec.offlineUuid(session.username);
    }

    private void sendEncryptionRequest(ChannelHandlerContext ctx) {
        ByteBuf enc = Unpooled.buffer();
        McCodec.writeVarInt(enc, 0x01);
        McCodec.writeString(enc, "");
        byte[] pub = session.server.rsaKeyPair().getPublic().getEncoded();
        McCodec.writeVarInt(enc, pub.length);
        enc.writeBytes(pub);
        McCodec.writeVarInt(enc, session.verifyToken.length);
        enc.writeBytes(session.verifyToken);
        if (session.protocolVersion >= 766) {
            enc.writeBoolean(true);
        }
        ctx.writeAndFlush(enc);
    }

    private void applyFloodgateIdentity() {
        FloodgateForwarder fg = session.server.floodgate();
        if (!fg.enabled()) {
            return;
        }
        fg.resolve(session.virtualHost, session.playerId, session.username).ifPresent(id -> {
            session.playerId = id.uuid();
            session.username = id.username();
            LOG.info("Floodgate identity " + session.username + " xuid=" + id.xuid() + " linked=" + id.linked());
        });
    }

    private void verifyTokenFromBuf(ByteBuf buf) throws Exception {
        int tokenLen = McCodec.readVarInt(buf);
        byte[] tokenEnc = new byte[tokenLen];
        buf.readBytes(tokenEnc);
        byte[] token = MinecraftCrypto.decryptRsa(session.server.rsaKeyPair(), tokenEnc);
        if (!Arrays.equals(token, session.verifyToken)) {
            throw new IllegalStateException("Invalid verify token");
        }
    }

    private void sendBackendHandshake(LinkConfig.Backend target) {
        ByteBuf hs = Unpooled.buffer();
        McCodec.writeVarInt(hs, 0x00);
        McCodec.writeVarInt(hs, session.protocolVersion);
        String hostField = target.host();
        if (session.floodgatePayload != null && session.server.floodgate().enabled()) {
            hostField = session.server.floodgate().forwardingHostname(hostField, session.floodgatePayload);
        }
        McCodec.writeString(hs, hostField);
        hs.writeShort(target.port());
        McCodec.writeVarInt(hs, 2);
        session.backend.writeAndFlush(hs);
    }

    private void sendBackendLoginStart() {
        ByteBuf login = Unpooled.buffer();
        McCodec.writeVarInt(login, 0x00);
        McCodec.writeString(login, session.username);
        McCodec.writeUuid(login, session.playerId);
        session.backend.writeAndFlush(login);
    }
}

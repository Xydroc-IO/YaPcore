package com.yapcore.link;

import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.protocol.McFrameCodec;
import com.yapcore.link.protocol.McOutboundPacketEncoder;
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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Velocity-style in-proxy backend swap for 1.20.2+ (protocol ≥ 764).
 * Keeps the client TCP connection; moves play → configuration → new backend play.
 */
final class ClientSessionSoftSwitch {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Client");

    /** Play clientbound {@code start_configuration} — 26.2 id 118. */
    private static final int PLAY_CB_START_CONFIGURATION = 118;
    /** Play serverbound {@code configuration_acknowledged} — 26.2 id 16. */
    private static final int PLAY_SB_CONFIGURATION_ACK = 16;
    /** Configuration {@code finish_configuration} both directions — id 3. */
    private static final int CONFIG_FINISH = 3;
    /** Login serverbound {@code login_acknowledged}. */
    private static final int LOGIN_SB_ACKNOWLEDGED = 0x03;
    /** Minimum protocol with configuration phase (1.20.2). */
    private static final int MIN_CONFIG_PROTOCOL = 764;

    private ClientSessionSoftSwitch() {
    }

    static boolean supports(int protocolVersion) {
        return protocolVersion >= MIN_CONFIG_PROTOCOL;
    }

    /**
     * Auto-swap the player to {@code target} without disconnecting the client.
     * Caller must have already validated target / permissions / events.
     */
    static void begin(ClientSession session, LinkConfig.Backend target) {
        Channel client = session.clientCtx != null ? session.clientCtx.channel() : null;
        if (client == null || !client.isActive()) {
            return;
        }
        if (!supports(session.protocolVersion)) {
            LOG.warning("SOFT-SWITCH unsupported proto=" + session.protocolVersion
                    + " user=" + session.username + " — falling back to reconnect");
            fallbackReconnect(session, target, client);
            return;
        }
        if (!session.switching.compareAndSet(false, true)) {
            session.sendPlaySystemChat(client,
                    com.yapcore.link.protocol.PlayChat.jsonText("Already switching servers…"));
            return;
        }

        LOG.info("SWITCH user=" + session.username + " → " + target.name() + " via soft");
        session.pendingSwitchTarget = target.name();

        // Replace client play relay first, then drop backend relay so closing the old
        // backend cannot cascade-close the client (PlayRelay.channelInactive).
        try {
            if (client.pipeline().get("to-backend") != null) {
                client.pipeline().replace("to-backend", "switch-client", new SwitchClientHandler(session));
            } else if (client.pipeline().get("switch-client") == null) {
                client.pipeline().addLast("switch-client", new SwitchClientHandler(session));
            }
        } catch (Exception e) {
            session.switching.set(false);
            LOG.log(Level.WARNING, "SOFT-SWITCH failed to install client handler", e);
            session.kickChannel(client, "Switch failed");
            return;
        }

        Channel oldBackend = session.backend;
        session.backend = null;
        if (oldBackend != null) {
            try {
                if (oldBackend.pipeline().get("to-client") != null) {
                    oldBackend.pipeline().remove("to-client");
                }
            } catch (Exception ignored) {
                // already gone
            }
            // Wait for old backend quit + YaPPlayerData session unlock before new login.
            Channel clientRef = client;
            LinkConfig.Backend targetRef = target;
            oldBackend.close().addListener(f -> clientRef.eventLoop().schedule(
                    () -> {
                        if (!clientRef.isActive() || !session.switching.get()) {
                            return;
                        }
                        connectBackendForSwitch(session, clientRef, targetRef);
                    },
                    1000L,
                    java.util.concurrent.TimeUnit.MILLISECONDS));
        } else {
            connectBackendForSwitch(session, client, target);
        }
    }

    private static void fallbackReconnect(ClientSession session, LinkConfig.Backend target, Channel client) {
        session.server.redirects().put(session.playerId, target.name());
        LOG.info("SWITCH user=" + session.username + " → " + target.name() + " via reconnect");
        Channel backend = session.backend;
        if (backend != null && backend.isActive()) {
            backend.close();
        }
        session.kickChannel(client,
                "§aJoining " + target.name() + "§7… Use Back to Server List, then join again.");
    }

    private static void connectBackendForSwitch(ClientSession session, Channel client, LinkConfig.Backend target) {
        connectBackendForSwitch(session, client, target, 0);
    }

    private static void connectBackendForSwitch(
            ClientSession session, Channel client, LinkConfig.Backend target, int attempt) {
        session.currentBackendName = target.name();
        session.phase = ClientSession.Phase.CONNECTING_BACKEND;
        session.resetProxyChannelsRegistered();

        LOG.info("CONNECT (switch) user=" + session.username + " → " + target.name()
                + " (" + target.host() + ":" + target.port() + ") proto=" + session.protocolVersion
                + (attempt > 0 ? " attempt=" + (attempt + 1) : ""));

        Bootstrap b = new Bootstrap();
        b.group(client.eventLoop())
                .channel(NioSocketChannel.class)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        session.server.config().connectTimeoutMs())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("frame-dec", new McFrameCodec.Decoder())
                                .addLast("frame-enc", new McOutboundPacketEncoder())
                                .addLast("backend", new SwitchBackendLoginHandler(session, client));
                    }
                });

        b.connect(target.host(), target.port()).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                LOG.warning("SOFT-SWITCH backend fail " + target.name() + ": " + f.cause()
                        + (attempt > 0 ? " attempt=" + (attempt + 1) : ""));
                // Fleet restarts briefly refuse TCP — retry before giving up.
                if (attempt < 8 && client.isActive() && session.switching.get()) {
                    long delayMs = 400L * (attempt + 1);
                    client.eventLoop().schedule(
                            () -> connectBackendForSwitch(session, client, target, attempt + 1),
                            delayMs,
                            java.util.concurrent.TimeUnit.MILLISECONDS);
                    return;
                }
                session.switching.set(false);
                // Keep the redirect so a manual rejoin still lands on the target.
                fallbackReconnect(session, target, client);
                return;
            }
            session.backend = f.channel();
            sendBackendHandshake(session, target);
            sendBackendLoginStart(session);
        });
    }

    private static void sendBackendHandshake(ClientSession session, LinkConfig.Backend target) {
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

    private static void sendBackendLoginStart(ClientSession session) {
        ByteBuf login = Unpooled.buffer();
        McCodec.writeVarInt(login, 0x00);
        McCodec.writeString(login, session.username);
        McCodec.writeUuid(login, session.playerId);
        session.backend.writeAndFlush(login);
    }

    private static void sendStartConfiguration(Channel client) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, PLAY_CB_START_CONFIGURATION);
        client.writeAndFlush(buf);
    }

    private static void sendLoginAcknowledged(Channel backend) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, LOGIN_SB_ACKNOWLEDGED);
        backend.writeAndFlush(buf);
    }

    /** Client-side handler during soft switch (play → config → play). */
    private static final class SwitchClientHandler extends ChannelInboundHandlerAdapter {
        private final ClientSession session;
        private final AtomicBoolean configAcked = new AtomicBoolean(false);
        private final AtomicBoolean finishSent = new AtomicBoolean(false);

        SwitchClientHandler(ClientSession session) {
            this.session = session;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) {
                return;
            }
            if (!buf.isReadable()) {
                buf.release();
                return;
            }
            buf.markReaderIndex();
            int packetId;
            try {
                packetId = McCodec.readVarInt(buf);
            } catch (Exception e) {
                buf.release();
                return;
            }

            // Waiting for play configuration_acknowledged after start_configuration.
            if (!configAcked.get()) {
                if (packetId == PLAY_SB_CONFIGURATION_ACK) {
                    buf.release();
                    if (configAcked.compareAndSet(false, true)) {
                        LOG.info("SOFT-SWITCH config-ack user=" + session.username);
                        session.clientInConfig = true;
                        Channel backend = session.backend;
                        if (backend != null && backend.isActive()) {
                            // Resume config forwarding from backend.
                            Object h = backend.pipeline().get("backend");
                            if (h instanceof SwitchBackendLoginHandler switchBackend) {
                                switchBackend.onClientConfigReady();
                            }
                        }
                    }
                    return;
                }
                // Drop play packets while waiting for config ack.
                buf.release();
                return;
            }

            // In configuration: forward to backend; watch finish_configuration.
            if (packetId == CONFIG_FINISH) {
                if (!finishSent.compareAndSet(false, true)) {
                    buf.release();
                    return;
                }
                LOG.info("SOFT-SWITCH finish-ack user=" + session.username
                        + " → play bridge on " + session.currentBackendName);
                session.clientInConfig = false;
                session.switching.set(false);
                session.pendingSwitchTarget = null;
                Channel backend = session.backend;
                // Install play relays BEFORE acking the backend so Join Game hits them.
                session.rebridgeAfterSwitch(ctx.channel(), backend);
                buf.resetReaderIndex();
                if (backend != null && backend.isActive()) {
                    backend.writeAndFlush(buf);
                } else {
                    buf.release();
                }
                return;
            }

            buf.resetReaderIndex();
            Channel backend = session.backend;
            if (backend != null && backend.isActive()) {
                backend.writeAndFlush(buf);
            } else {
                buf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            session.switching.set(false);
            Channel backend = session.backend;
            if (backend != null) {
                backend.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.log(Level.WARNING, "soft-switch client error user=" + session.username, cause);
            session.switching.set(false);
            ctx.close();
            if (session.backend != null) {
                session.backend.close();
            }
        }
    }

    /**
     * Backend login during soft switch: suppress Login Success to client, ACK login,
     * then enter config passthrough after client acknowledges start_configuration.
     */
    private static final class SwitchBackendLoginHandler extends ChannelInboundHandlerAdapter {
        private final ClientSession session;
        private final Channel client;
        private boolean forwarded;
        private boolean loginSuccessSeen;
        private boolean clientConfigReady;
        private final java.util.ArrayList<ByteBuf> pendingConfig = new java.util.ArrayList<>();
        private com.yapcore.link.protocol.McCompressionCodec.Decoder backendCompDec;

        SwitchBackendLoginHandler(ClientSession session, Channel client) {
            this.session = session;
            this.client = client;
        }

        void onClientConfigReady() {
            clientConfigReady = true;
            Channel backend = ctxRef != null ? ctxRef.channel() : session.backend;
            if (backend != null && backend.isActive() && loginSuccessSeen) {
                sendLoginAcknowledged(backend);
                backend.config().setAutoRead(true);
            }
            for (ByteBuf pending : pendingConfig) {
                if (client.isActive()) {
                    client.writeAndFlush(pending);
                } else {
                    pending.release();
                }
            }
            pendingConfig.clear();
            if (loginSuccessSeen && ctxRef != null) {
                enterConfigRelay(ctxRef);
            }
        }

        private ChannelHandlerContext ctxRef;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            ctxRef = ctx;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) {
                return;
            }
            buf.markReaderIndex();
            int packetId = McCodec.readVarInt(buf);
            try {
                if (!loginSuccessSeen) {
                    handleLoginPhase(ctx, buf, packetId);
                    return;
                }
                // Configuration packets from backend.
                buf.resetReaderIndex();
                if (!clientConfigReady) {
                    pendingConfig.add(buf.retain());
                    buf.release();
                    return;
                }
                if (packetId == CONFIG_FINISH) {
                    // Forward finish_configuration; client ack triggers rebridge.
                    buf.resetReaderIndex();
                    client.writeAndFlush(buf);
                    return;
                }
                buf.resetReaderIndex();
                client.writeAndFlush(buf);
            } catch (Exception e) {
                buf.release();
                LOG.log(Level.WARNING, "soft-switch backend login failed", e);
                session.switching.set(false);
                session.kickChannel(client, "Backend switch error");
                ctx.close();
            }
        }

        private void handleLoginPhase(ChannelHandlerContext ctx, ByteBuf buf, int packetId) throws Exception {
            if (packetId == 0x00) {
                // Login disconnect — never forward raw login packets to a play client
                // (id 0 looks like play bundle_delimiter → DecoderException).
                String reason = "Backend rejected switch";
                try {
                    reason = McCodec.readString(buf, 32767);
                } catch (Exception ignored) {
                    // keep default
                }
                buf.release();
                session.switching.set(false);
                session.kickChannel(client, reason);
                ctx.close();
                return;
            }
            if (packetId == 0x03) {
                int threshold = McCodec.readVarInt(buf);
                buf.release();
                // Client already has compression from first join — only update backend codec.
                enableBackendCompression(ctx.channel(), threshold);
                // Keep client threshold in sync if backend differs.
                Object clientEnc = client.pipeline().get("frame-enc");
                if (clientEnc instanceof McOutboundPacketEncoder enc) {
                    enc.setCompressionThreshold(threshold);
                }
                Object clientDec = client.pipeline().get("comp-dec");
                if (clientDec instanceof com.yapcore.link.protocol.McCompressionCodec.Decoder dec) {
                    dec.setThreshold(threshold);
                } else if (threshold >= 0) {
                    var dec = new com.yapcore.link.protocol.McCompressionCodec.Decoder();
                    client.pipeline().addAfter("frame-dec", "comp-dec", dec);
                    dec.setThreshold(threshold);
                }
                return;
            }
            if (packetId == 0x04) {
                handlePluginRequest(ctx, buf);
                return;
            }
            if (packetId == 0x05) {
                // Login cookie_request (1.20.5+) — reply empty; never forward to play client.
                handleLoginCookieRequest(ctx, buf);
                return;
            }
            if (packetId == 0x02) {
                buf.release(); // do NOT send Login Success to an already-playing client
                loginSuccessSeen = true;
                if (!forwarded) {
                    LOG.info("SOFT-SWITCH login-success (no modern fwd) user=" + session.username);
                } else {
                    LOG.info("SOFT-SWITCH login-success user=" + session.username);
                }
                // Pause backend until the client enters configuration (Velocity order).
                ctx.channel().config().setAutoRead(false);
                sendStartConfiguration(client);
                // Login ACK after client config-ack (onClientConfigReady).
                return;
            }
            buf.release();
            session.switching.set(false);
            LOG.warning("SOFT-SWITCH unexpected login packet 0x" + Integer.toHexString(packetId)
                    + " user=" + session.username);
            session.kickChannel(client, "Unexpected backend packet during switch");
            ctx.close();
        }

        private void handleLoginCookieRequest(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
            String key = McCodec.readString(buf, 32767);
            buf.release();
            ByteBuf resp = Unpooled.buffer();
            McCodec.writeVarInt(resp, 0x04); // login cookie_response
            McCodec.writeString(resp, key);
            resp.writeBoolean(false);
            ctx.writeAndFlush(resp);
            LOG.fine("SOFT-SWITCH cookie_request key=" + key + " user=" + session.username);
        }

        private void enterConfigRelay(ChannelHandlerContext ctx) {
            // Stay as this handler — channelRead already relays config once loginSuccessSeen.
            session.phase = ClientSession.Phase.BRIDGING;
        }

        private void handlePluginRequest(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
            int messageId = McCodec.readVarInt(buf);
            String channel = McCodec.readString(buf, 32767);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            buf.release();
            if (com.yapcore.link.forwarding.ModernForwarding.CHANNEL.equals(channel)) {
                ByteBuf payload = com.yapcore.link.forwarding.ModernForwarding.createForwardingData(
                        session.server.config().forwardingSecret(),
                        session.clientAddress,
                        session.playerId,
                        session.username,
                        session.properties
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

        private void enableBackendCompression(Channel backendCh, int threshold) {
            if (threshold < 0) {
                return;
            }
            if (backendCompDec == null) {
                backendCompDec = new com.yapcore.link.protocol.McCompressionCodec.Decoder();
                backendCh.pipeline().addAfter("frame-dec", "comp-dec", backendCompDec);
            }
            backendCompDec.setThreshold(threshold);
            Object backendEnc = backendCh.pipeline().get("frame-enc");
            if (backendEnc instanceof McOutboundPacketEncoder enc) {
                enc.setCompressionThreshold(threshold);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (session.switching.get()) {
                LOG.warning("SOFT-SWITCH backend dropped during switch user=" + session.username);
                session.switching.set(false);
                if (client.isActive()) {
                    session.kickChannel(client, "Lost connection while switching servers");
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.log(Level.WARNING, "soft-switch backend error", cause);
            session.switching.set(false);
            ctx.close();
            if (client.isActive()) {
                session.kickChannel(client, "Backend switch error");
            }
        }
    }
}

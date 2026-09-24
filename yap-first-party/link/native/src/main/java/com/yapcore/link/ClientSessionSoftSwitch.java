package com.yapcore.link;

import com.yapcore.link.bedrock.FailoverSpawnArrival;
import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.protocol.McFrameCodec;
import com.yapcore.link.protocol.McOutboundPacketEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Velocity-style in-proxy backend swap for 1.20.2+ (protocol ≥ 764).
 * Keeps the client TCP connection; moves play → configuration → new backend play.
 */
final class ClientSessionSoftSwitch {

    static final Logger LOG = Logger.getLogger("YaP.Link.Client");

    /** Play clientbound {@code start_configuration} — 26.2 id 118. */
    private static final int PLAY_CB_START_CONFIGURATION = 118;
    /** Play serverbound {@code configuration_acknowledged} — 26.2 id 16. */
    static final int PLAY_SB_CONFIGURATION_ACK = 16;
    /** Configuration {@code finish_configuration} both directions — id 3. */
    static final int CONFIG_FINISH = 3;
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
            // Still mark spawn so reconnect lands at /setspawn when no portal pending.
            FailoverSpawnArrival.markIfAbsent(
                    session.server.config().home(), session.playerId, target.name());
            fallbackReconnect(session, target, client);
            return;
        }
        if (!session.switching.compareAndSet(false, true)) {
            session.sendPlaySystemChat(client,
                    com.yapcore.link.protocol.PlayChat.jsonText("Already switching servers…"));
            return;
        }

        // /hub /server SoftSwitch: land at destination /setspawn. Portal Connect already
        // wrote island/rtp/home pending — markIfAbsent must not overwrite those.
        FailoverSpawnArrival.markIfAbsent(
                session.server.config().home(), session.playerId, target.name());

        LOG.info("SWITCH user=" + session.username + " → " + target.name() + " via soft");
        session.pendingSwitchTarget = target.name();

        // Replace client play relay first, then drop backend relay so closing the old
        // backend cannot cascade-close the client (PlayRelay.channelInactive).
        try {
            if (client.pipeline().get("to-backend") != null) {
                client.pipeline().replace("to-backend", "switch-client", new SoftSwitchClientHandler(session));
            } else if (client.pipeline().get("switch-client") == null) {
                client.pipeline().addLast("switch-client", new SoftSwitchClientHandler(session));
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
            oldBackend.close().addListener(f -> scheduleUnlockThenConnect(session, clientRef, targetRef, 0));
        } else {
            scheduleUnlockThenConnect(session, client, target, 0);
        }
    }

    /**
     * Poll session unlock (or clear stale locks) without blocking the Netty event loop.
     * ~75ms × 40 ≈ 3s max — matches PlayerData quit releasing the lock first.
     */
    private static void scheduleUnlockThenConnect(
            ClientSession session, Channel client, LinkConfig.Backend target, int attempt) {
        if (!client.isActive() || !session.switching.get()) {
            return;
        }
        var gate = com.yapcore.link.api.SessionUnlockGate.Holder.get();
        if (gate == null) {
            if (attempt == 0) {
                client.eventLoop().schedule(
                        () -> scheduleUnlockThenConnect(session, client, target, 1),
                        400L,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
                return;
            }
            connectBackendForSwitch(session, client, target);
            return;
        }
        boolean ready = gate.isReady(
                session.playerId,
                target.name(),
                name -> session.server.backendMonitor().isUp(name));
        if (ready) {
            connectBackendForSwitch(session, client, target);
            return;
        }
        if (attempt >= 40) {
            LOG.info("SOFT-SWITCH unlock timeout user=" + session.username
                    + " — force-clearing lock → " + target.name());
            gate.forceClear(session.playerId);
            connectBackendForSwitch(session, client, target);
            return;
        }
        client.eventLoop().schedule(
                () -> scheduleUnlockThenConnect(session, client, target, attempt + 1),
                75L,
                java.util.concurrent.TimeUnit.MILLISECONDS);
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
                                .addLast("backend", new SoftSwitchBackendLoginHandler(session, client));
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

    static void sendStartConfiguration(Channel client) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, PLAY_CB_START_CONFIGURATION);
        client.writeAndFlush(buf);
    }

    static void sendLoginAcknowledged(Channel backend) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, LOGIN_SB_ACKNOWLEDGED);
        backend.writeAndFlush(buf);
    }
}

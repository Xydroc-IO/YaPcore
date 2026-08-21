package com.yapcore.protocol.java;

import com.yapcore.client.ClientEdition;
import com.yapcore.config.ServerConfig;
import com.yapcore.protocol.DualStackGateway;
import com.yapcore.protocol.ProtocolVersionRegistry;
import com.yapcore.protocol.compat.ProtocolCompat;
import com.yapcore.protocol.java.chunk.ChunkPacketEncoder;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.java.mod.ModLoaderCompat;
import com.yapcore.world.WorldServer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Java Edition connection state machine.
 * Speaks the client's native {@link ProtocolBand} — built-in multi-version, no translators.
 */
public final class JavaProtocolHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger LOG = Logger.getLogger("YaPcore.JE");
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(1000);

    private final DualStackGateway gateway;
    private final ServerConfig config;
    private final ProtocolVersionRegistry protocols;
    private final ModLoaderCompat modCompat = new ModLoaderCompat();

    private ConnState state = ConnState.HANDSHAKE;
    private int protocolVersion = 776;
    private ProtocolBand band = ProtocolBand.V26_2;
    private String username = "Player";
    private UUID playerUuid = UUID.randomUUID();
    private boolean knownPacksMatched;
    private boolean joinedPlay;
    private boolean finishConfigSent;
    private String mcVersionLabel = "26.2";
    private String lastInbound = "none";
    private String joinPhase = "handshake";
    private ScheduledFuture<?> keepAliveTask;
    private ScheduledFuture<?> finishAckWatchdog;
    private final AtomicLong keepAliveId = new AtomicLong(1);

    public JavaProtocolHandler(DualStackGateway gateway,
                               ServerConfig config,
                               ProtocolVersionRegistry protocols) {
        this.gateway = gateway;
        this.config = config;
        this.protocols = protocols;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        if (!msg.isReadable()) {
            return;
        }
        if (state == ConnState.HANDSHAKE && msg.getUnsignedByte(msg.readerIndex()) == 0xFE) {
            logJoin("legacy-ping 0xFE — closing");
            ctx.close();
            return;
        }
        int packetId = McCodec.readVarInt(msg);
        lastInbound = state.name() + "/0x" + Integer.toHexString(packetId);
        switch (state) {
            case HANDSHAKE -> handleHandshake(ctx, packetId, msg);
            case STATUS -> handleStatus(ctx, packetId, msg);
            case LOGIN -> handleLogin(ctx, packetId, msg);
            case CONFIG -> handleConfig(ctx, packetId, msg);
            case PLAY -> handlePlay(ctx, packetId, msg);
        }
    }

    private void handleHandshake(ChannelHandlerContext ctx, int packetId, ByteBuf msg) {
        if (packetId != 0x00) {
            logJoinFail("bad handshake packet id=0x" + Integer.toHexString(packetId));
            ctx.close();
            return;
        }
        protocolVersion = McCodec.readVarInt(msg);
        band = ProtocolCompat.bandFor(protocolVersion);
        String host = McCodec.readString(msg, 255);
        int port = msg.readUnsignedShort();
        int intent = McCodec.readVarInt(msg);
        String forgeTag = ModLoaderCompat.detectFromHandshakeHost(host);
        if (forgeTag != null) {
            modCompat.noteBrand(forgeTag);
        }
        protocols.resolve(ClientEdition.JAVA, protocolVersion).ifPresentOrElse(v -> {
            mcVersionLabel = v.minecraftVersion();
            LOG.info("JE handshake protocol=" + protocolVersion
                    + " band=" + band.name()
                    + " → " + v.minecraftVersion()
                    + " host=" + host + ":" + port + " intent=" + intent);
        }, () -> {
            mcVersionLabel = "unknown-" + protocolVersion;
            LOG.info("JE handshake protocol=" + protocolVersion
                    + " band=" + band.name() + " (nearest native band)"
                    + " host=" + host + ":" + port + " intent=" + intent);
        });
        if (intent == 1) {
            state = ConnState.STATUS;
            joinPhase = "status";
        } else if (intent == 2 || intent == 3) {
            state = ConnState.LOGIN;
            joinPhase = "login";
        } else {
            logJoinFail("unknown handshake intent=" + intent);
            ctx.close();
        }
    }

    private void handleStatus(ChannelHandlerContext ctx, int packetId, ByteBuf msg) {
        if (packetId == 0x00) {
            String json = StatusJson.build(config, protocols, protocolVersion,
                    gateway.getClients().size());
            PacketFactory.send(ctx.channel(), PacketFactory.statusResponse(json));
        } else if (packetId == 0x01) {
            long payload = msg.readLong();
            PacketFactory.send(ctx.channel(), PacketFactory.statusPong(payload));
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, int packetId, ByteBuf msg) {
        switch (packetId) {
            case 0x00 -> {
                username = McCodec.readString(msg, 16);
                if (msg.readableBytes() >= 16) {
                    playerUuid = McCodec.readUuid(msg);
                } else {
                    playerUuid = McCodec.offlineUuid(username);
                }
                if (config.isOnlineMode()) {
                    logJoinFail("online-mode=true but auth not implemented");
                    PacketFactory.send(ctx.channel(), PacketFactory.loginDisconnect(
                            "Online-mode auth not enabled on this build — set online-mode=false"));
                    ctx.close();
                    return;
                }
                PacketFactory.send(ctx.channel(),
                        PacketFactory.loginSuccess(protocolVersion, playerUuid, username));
                LOG.info("Login Success for " + username + " uuid=" + playerUuid
                        + " protocol=" + protocolVersion + " band=" + band.name()
                        + " sessionId=" + (band.loginIncludesSessionId() ? "yes" : "no"));
                joinPhase = "login-success-sent";
                if (!band.hasConfigurationPhase()) {
                    enterPlay(ctx);
                }
            }
            case 0x02 -> {
                int id = McCodec.readVarInt(msg);
                boolean has = msg.isReadable() && msg.readBoolean();
                LOG.info("Login plugin response id=" + id + " understood=" + has
                        + " for " + username);
                modCompat.onLoginPluginResponse(ctx.channel(), id, has, msg);
            }
            case 0x03 -> {
                state = ConnState.CONFIG;
                joinPhase = "config";
                LOG.info("Login Acknowledged → CONFIG for " + username
                        + " (sending Feature Flags + Known Packs @" + mcVersionLabel + ")");
                PacketFactory.send(ctx.channel(), PacketFactory.updateEnabledFeatures());
                PacketFactory.send(ctx.channel(),
                        PacketFactory.knownPacksClientbound(mcVersionLabel));
            }
            default -> LOG.warning("Unhandled LOGIN packet id=0x" + Integer.toHexString(packetId)
                    + " for " + username + " remaining=" + msg.readableBytes());
        }
    }

    private void handleConfig(ChannelHandlerContext ctx, int packetId, ByteBuf msg) {
        switch (packetId) {
            case 0x00 -> LOG.info("CONFIG Client Information from " + username
                    + " (" + msg.readableBytes() + " bytes)");
            case 0x02 -> {
                String channel = McCodec.readString(msg, 32767);
                modCompat.noteChannel(channel);
                LOG.info("CONFIG custom_payload channel=" + channel + " from " + username);
                if (channel.contains("brand")) {
                    try {
                        int len = McCodec.readVarInt(msg);
                        byte[] b = new byte[Math.min(len, msg.readableBytes())];
                        msg.readBytes(b);
                        modCompat.noteBrand(new String(b));
                    } catch (Exception e) {
                        LOG.warning("Failed to parse brand payload: " + e.getMessage());
                    }
                }
            }
            case 0x03 -> {
                LOG.info("Acknowledge Finish Configuration from " + username
                        + " → entering PLAY");
                cancelFinishWatchdog();
                enterPlay(ctx);
            }
            case 0x07 -> {
                int count = McCodec.readVarInt(msg);
                knownPacksMatched = false;
                String echoedVersion = null;
                for (int i = 0; i < count; i++) {
                    String ns = McCodec.readString(msg, 32767);
                    String id = McCodec.readString(msg, 32767);
                    String ver = McCodec.readString(msg, 32767);
                    LOG.info("Known pack offer from client: " + ns + ":" + id + "@" + ver);
                    if ("minecraft".equals(ns) && "core".equals(id)) {
                        knownPacksMatched = true;
                        echoedVersion = ver;
                        mcVersionLabel = ver;
                    }
                }
                if (!knownPacksMatched) {
                    LOG.warning("Client sent " + count + " known pack(s) but no minecraft:core "
                            + "(offered " + mcVersionLabel + ") — NBT fallback");
                } else {
                    LOG.info("Known pack matched: minecraft:core@" + echoedVersion);
                }
                joinPhase = "registries+tags";
                try {
                    String dumpVersion = VanillaProtocolData.resolveDumpVersion(
                            mcVersionLabel, protocolVersion);
                    RegistryBootstrap.sendVanillaComplete(
                            ctx.channel(), knownPacksMatched, dumpVersion);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Registry/tag bootstrap failed for " + username, e);
                    ctx.close();
                    return;
                }
                PacketFactory.send(ctx.channel(), PacketFactory.finishConfiguration());
                finishConfigSent = true;
                joinPhase = "await-finish-ack";
                LOG.info("Finish Configuration sent to " + username
                        + " — waiting for Acknowledge (CONFIG 0x03)."
                        + " If client disconnects here, check client for Network Protocol Error"
                        + " (missing registry/tag requirements).");
                armFinishWatchdog(ctx);
            }
            case 0x09 -> LOG.info("Accept Code of Conduct from " + username);
            default -> LOG.warning("Unhandled CONFIG packet id=0x" + Integer.toHexString(packetId)
                    + " for " + username + " remaining=" + msg.readableBytes()
                    + " phase=" + joinPhase);
        }
    }

    private void armFinishWatchdog(ChannelHandlerContext ctx) {
        cancelFinishWatchdog();
        finishAckWatchdog = ctx.executor().schedule(() -> {
            if (!joinedPlay && finishConfigSent && ctx.channel().isActive()) {
                LOG.warning("JOIN STALL: " + username + " did not Acknowledge Finish Configuration "
                        + "within 8s. phase=" + joinPhase + " lastInbound=" + lastInbound
                        + " knownPacks=" + knownPacksMatched
                        + " — client likely rejected registries/tags (Network Protocol Error).");
            }
        }, 8, TimeUnit.SECONDS);
    }

    private void cancelFinishWatchdog() {
        if (finishAckWatchdog != null) {
            finishAckWatchdog.cancel(false);
            finishAckWatchdog = null;
        }
    }

    private void enterPlay(ChannelHandlerContext ctx) {
        if (joinedPlay) {
            return;
        }
        joinedPlay = true;
        state = ConnState.PLAY;
        joinPhase = "play";
        modCompat.logAccepted(username);
        int eid = ENTITY_IDS.incrementAndGet();
        WorldServer world = WorldServer.overworld();
        try {
            PacketFactory.send(ctx.channel(), PacketFactory.playLogin(protocolVersion, config, eid));
            LOG.info("PLAY Login (join_game) sent eid=" + eid + " to " + username);
            PacketFactory.send(ctx.channel(),
                    PacketFactory.playerAbilities(protocolVersion, true, true));
            int view = Math.max(2, Math.min(8, config.getViewDistance()));
            int spawnChunkX = (int) Math.floor(world.spawnX()) >> 4;
            int spawnChunkZ = (int) Math.floor(world.spawnZ()) >> 4;
            PacketFactory.send(ctx.channel(),
                    PacketFactory.setCenterChunk(protocolVersion, spawnChunkX, spawnChunkZ));
            int sent = 0;
            for (int dx = -view; dx <= view; dx++) {
                for (int dz = -view; dz <= view; dz++) {
                    var col = world.getOrCreateChunk(spawnChunkX + dx, spawnChunkZ + dz);
                    PacketFactory.send(ctx.channel(), ChunkPacketEncoder.encode(col));
                    sent++;
                }
            }
            LOG.info("Streamed " + sent + " native chunks (view=" + view + ") to " + username);
            PacketFactory.send(ctx.channel(), PacketFactory.gameEvent(protocolVersion, 13, 0f));
            PacketFactory.send(ctx.channel(), PacketFactory.playerPosition(
                    protocolVersion, world.spawnX(), world.spawnY(), world.spawnZ()));
            PacketFactory.send(ctx.channel(),
                    modCompat.brandPluginMessage(protocolVersion, "yapcore"));
            startKeepAlive(ctx);
            InetSocketAddress addr = (InetSocketAddress) ctx.channel().remoteAddress();
            gateway.acceptClient(username, ClientEdition.JAVA, protocolVersion, addr);
            LOG.info("JE PLAY joined " + username + " loader=" + modCompat.hint()
                    + " protocol=" + protocolVersion + " band=" + band.name()
                    + " world=" + world.name()
                    + " remote=" + addr);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed during PLAY enter for " + username, e);
            ctx.close();
        }
    }

    private void startKeepAlive(ChannelHandlerContext ctx) {
        stopKeepAlive();
        keepAliveTask = ctx.executor().scheduleAtFixedRate(() -> {
            if (!ctx.channel().isActive() || state != ConnState.PLAY) {
                stopKeepAlive();
                return;
            }
            long id = keepAliveId.getAndIncrement();
            PacketFactory.send(ctx.channel(), PacketFactory.keepAlive(protocolVersion, id));
        }, 5, 10, TimeUnit.SECONDS);
    }

    private void stopKeepAlive() {
        if (keepAliveTask != null) {
            keepAliveTask.cancel(false);
            keepAliveTask = null;
        }
    }

    private void handlePlay(ChannelHandlerContext ctx, int packetId, ByteBuf msg) {
        if (packetId == band.keepAliveSbId()) {
            return;
        }
        // 26.2: accept_teleportation=0, move_player_pos=30, pos_rot=31, rot=32, status=33
        if (packetId == 0x00) {
            if (msg.isReadable()) {
                McCodec.readVarInt(msg); // teleport id
            }
            return;
        }
        if (protocolVersion >= 776 && (packetId == 30 || packetId == 31)) {
            double x = msg.readDouble();
            double y = msg.readDouble();
            double z = msg.readDouble();
            if (packetId == 31 && msg.readableBytes() >= 8) {
                msg.readFloat();
                msg.readFloat();
            }
            if (msg.isReadable()) {
                msg.readByte(); // flags
            }
            LOG.fine("MOVE " + username + " → " + x + "," + y + "," + z);
            return;
        }
        if (protocolVersion >= 776 && packetId == 32) {
            if (msg.readableBytes() >= 8) {
                msg.readFloat();
                msg.readFloat();
            }
            if (msg.isReadable()) {
                msg.readByte();
            }
            return;
        }
        if (protocolVersion >= 776 && packetId == 33) {
            if (msg.isReadable()) {
                msg.readByte();
            }
            return;
        }
        LOG.fine("PLAY packet id=0x" + Integer.toHexString(packetId) + " from " + username);
    }

    private void logJoin(String msg) {
        LOG.info("JE join [" + username + "] " + msg);
    }

    private void logJoinFail(String reason) {
        LOG.warning("JE JOIN FAILED [" + username + "] state=" + state
                + " phase=" + joinPhase + " protocol=" + protocolVersion
                + " band=" + band.name() + " lastInbound=" + lastInbound
                + " reason=" + reason);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cancelFinishWatchdog();
        stopKeepAlive();
        if (!joinedPlay) {
            // Server-list pings (handshake intent=1) always close after status — not a join failure.
            if (state == ConnState.STATUS || "status".equals(joinPhase)) {
                LOG.fine("JE status ping closed protocol=" + protocolVersion + " band=" + band.name());
            } else {
                logJoinFail("connection closed before PLAY"
                        + " finishConfigSent=" + finishConfigSent
                        + " knownPacksMatched=" + knownPacksMatched);
            }
        } else {
            LOG.info("JE disconnect " + username + " (was in PLAY)");
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.log(Level.WARNING, "JE connection error for " + username
                + " state=" + state + " phase=" + joinPhase
                + " lastInbound=" + lastInbound + ": " + cause.getMessage(), cause);
        cancelFinishWatchdog();
        stopKeepAlive();
        ctx.close();
    }
}

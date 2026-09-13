package com.yapcore.crossplay.bedrock.geyserport;

import com.yapcore.crossplay.bedrock.BedrockGameplayBridge;
import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.bedrock.BedrockSessionManager;
import com.yapcore.crossplay.bedrock.bridge.BedrockBridgeContext;
import com.yapcore.crossplay.bedrock.bridge.BedrockJoinProbe;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstCodecIndex;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstPackets;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession;
import com.yapcore.crossplay.bedrock.codec.BedrockWorldCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cloudburstmc.math.vector.Vector2i;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTimePacket;

/**
 * Native port of Geyser {@code org.geysermc.geyser.session.GeyserSession} <b>join subset</b>.
 *
 * <p>Method names and order match Geyser: {@link #connect}, {@code startGame},
 * {@code buildStartGamePacket}, {@code configureExperiments}, {@code syncEntityProperties},
 * {@code sendRegistryDefinitions}, {@code sendInitialPlayerState}, {@code sendInitialGameRules},
 * {@link #resetTimeParameters}, {@link #setServerRenderDistance}
 * (StartGame helpers live in {@link YapGeyserSessionStartGame}).
 *
 * <p><b>Honest scope:</b> full Geyser = entire translator tree. This class is the full JOIN path
 * port. Inventory / entity / gameplay translators are not ported yet.
 *
 * <p>Outbound goes through DualStack / Bedrock bridge {@code sendPacket} hooks.
 * See {@code docs/geyser-join-reference/NATIVE_PORT.md}.
 */
public final class YapGeyserSession {

    /** Geyser practical default Java view when Paper/Java has not supplied one yet. */
    public static final int DEFAULT_JAVA_VIEW = 8;

    /** Vanilla Bedrock overworld height (Geyser {@code BedrockDimension.OVERWORLD}). */
    public static final int OVERWORLD_MIN_Y = -64;
    public static final int OVERWORLD_MAX_Y = 320;
    public static final int OVERWORLD_HEIGHT = OVERWORLD_MAX_Y - OVERWORLD_MIN_Y; // 384

    private final BedrockBridgeContext ctx;
    private final long guid;
    private final long runtimeId;
    private final String username;
    private final UUID uuid;
    private final int protocol;
    private final CloudburstSession cloudburst;

    private int spawnX;
    private int spawnY;
    private int spawnZ;
    private int minY = OVERWORLD_MIN_Y;
    private int maxY = OVERWORLD_MAX_Y;
    private int bedrockDimensionId = 0;
    private int serverRenderDistance = -1;
    /** Full Java view to apply after real 0x71 (pre-init may advertise a smaller radius — F2). */
    private int pendingFullRenderDistance = -1;
    private int clientRenderDistance = 0;
    private Vector2i lastChunkPosition;
    private boolean sentSpawnPacket;
    private boolean upstreamInitialized;
    private long dayTimeTicks;
    private float partialTimeTick;
    private float clockRate;
    private boolean shouldClientTickClock;

    /** Test-only: when set, {@link #sendUpstreamPacket} records instead of bridging. */
    private java.util.function.Consumer<BedrockPacket> testPacketSink;

    private YapGeyserSession(
            BedrockBridgeContext ctx,
            long guid,
            long runtimeId,
            String username,
            UUID uuid,
            int protocol,
            CloudburstSession cloudburst) {
        this.ctx = ctx;
        this.guid = guid;
        this.runtimeId = runtimeId;
        this.username = username;
        this.uuid = uuid;
        this.protocol = protocol;
        this.cloudburst = cloudburst;
    }

    /**
     * Open a per-player join session (GeyserSession analogue) after Floodgate auth + Cloudburst open.
     */
    public static YapGeyserSession open(
            BedrockBridgeContext ctx,
            long guid,
            long runtimeId,
            String username,
            UUID uuid,
            int protocol,
            CloudburstSession cloudburst) {
        return new YapGeyserSession(ctx, guid, runtimeId, username, uuid, protocol, cloudburst);
    }

    public long guid() {
        return guid;
    }

    public long runtimeId() {
        return runtimeId;
    }

    public String username() {
        return username;
    }

    public UUID uuid() {
        return uuid;
    }

    public int protocolVersion() {
        return protocol;
    }

    public CloudburstSession cloudburst() {
        return cloudburst;
    }

    public BedrockBridgeContext ctx() {
        return ctx;
    }

    public int getServerRenderDistance() {
        return serverRenderDistance;
    }

    public void setPendingFullRenderDistance(int view) {
        this.pendingFullRenderDistance = Math.max(2, Math.min(32, view));
    }

    public int pendingFullRenderDistance() {
        return pendingFullRenderDistance;
    }

    public int getClientRenderDistance() {
        return clientRenderDistance;
    }

    public Vector2i getLastChunkPosition() {
        return lastChunkPosition;
    }

    public void setLastChunkPosition(Vector2i pos) {
        this.lastChunkPosition = pos;
    }

    public boolean isSentSpawnPacket() {
        return sentSpawnPacket;
    }

    public boolean isUpstreamInitialized() {
        return upstreamInitialized;
    }

    public void setUpstreamInitialized(boolean initialized) {
        this.upstreamInitialized = initialized;
    }

    public int bedrockDimensionId() {
        return bedrockDimensionId;
    }

    public int bedrockDimensionHeight() {
        return Math.max(16, maxY - minY);
    }

    public int spawnX() {
        return spawnX;
    }

    public int spawnY() {
        return spawnY;
    }

    public int spawnZ() {
        return spawnZ;
    }

    public Vector3i spawnBlockPos() {
        return Vector3i.from(spawnX, spawnY, spawnZ);
    }

    /** Block definition for UpdateBlock forceUpdate (Geyser dim-switch path). */
    public BlockDefinition blockDefinitionOrAir(int runtimeIdHint) {
        if (cloudburst != null) {
            try {
                BlockDefinition def = cloudburst.palettes().blocks().getDefinition(runtimeIdHint);
                if (def != null) {
                    return def;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return new SimpleBlockDefinition("minecraft:air", runtimeIdHint, NbtMap.EMPTY);
    }

    /** Geyser {@code sendUpstreamPacket} → DualStack / Bedrock bridge encode. */
    public void sendUpstreamPacket(BedrockPacket packet) {
        if (packet == null) {
            return;
        }
        if (testPacketSink != null) {
            testPacketSink.accept(packet);
            return;
        }
        if (cloudburst != null) {
            ctx.sendPacket(guid, packet);
        } else {
            BedrockBridgeContext.LOG.fine(
                    "BE YapGeyserSession send without Cloudburst: " + packet.getClass().getSimpleName());
        }
    }

    /** Test hook: set spawn, capture connect() packets (no bridge send). */
    List<BedrockPacket> connectCollecting(int sx, int sy, int sz) {
        this.spawnX = sx;
        this.spawnY = sy;
        this.spawnZ = sz;
        List<BedrockPacket> out = new ArrayList<>();
        this.testPacketSink = out::add;
        try {
            connect();
        } finally {
            this.testPacketSink = null;
        }
        return out;
    }

    /** Test hook: capture setServerRenderDistance packets. */
    List<BedrockPacket> setServerRenderDistanceCollecting(int view) {
        List<BedrockPacket> out = new ArrayList<>();
        this.testPacketSink = out::add;
        try {
            setServerRenderDistance(view);
        } finally {
            this.testPacketSink = null;
        }
        return out;
    }

    public void sendUpstreamPackets(List<? extends BedrockPacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return;
        }
        if (cloudburst != null) {
            ctx.sendPackets(guid, packets);
        }
    }

    /**
     * Entry after empty resource-pack handshake COMPLETED.
     * Geyser: JavaLoginTranslator → {@code connect()} then {@code setServerRenderDistance}.
     * YaP: same Bedrock S2C side via {@link JavaLoginTranslator#afterConnect}.
     */
    public void join(List<BedrockGameplayBridge.GameAction> actions) {
        BedrockBridgeContext.LoginPhase prev = ctx.loginPhase.get(guid);
        if (prev == BedrockBridgeContext.LoginPhase.SPAWNED
                || prev == BedrockBridgeContext.LoginPhase.AWAITING_CLIENT_INIT
                || prev == BedrockBridgeContext.LoginPhase.BOOTSTRAPPING) {
            return;
        }
        BedrockSessionManager.BedrockSession session = ctx.sessions.get(guid);
        if (session == null) {
            return;
        }
        double[] spawn = paperSpawnOrDefault();
        spawnX = (int) Math.floor(spawn[0]);
        spawnY = (int) Math.floor(spawn[1]);
        spawnZ = (int) Math.floor(spawn[2]);
        ctx.pendingSpawn.put(guid, new BedrockBridgeContext.PendingSpawn(
                runtimeId, username, uuid, spawnX, spawnY, spawnZ, spawn));

        if (cloudburst == null && protocol >= CloudburstCodecIndex.MIN_MODERN) {
            BedrockBridgeContext.LOG.severe(
                    "BE YapGeyserSession.join missing CloudburstSession proto=" + protocol);
            return;
        }

        if (cloudburst != null) {
            BedrockBridgeContext.LOG.info(
                    "BE YapGeyserSession.join/connect codec=v" + cloudburst.codec().getProtocolVersion()
                            + " proto=" + protocol + " user=" + username);
            connect();
            JavaLoginTranslator.afterConnect(this, DEFAULT_JAVA_VIEW);
            ctx.loginPhase.put(guid, BedrockBridgeContext.LoginPhase.AWAITING_CLIENT_INIT);
            BedrockJoinProbe.noteEvent(guid, "connect+afterConnect done view=" + serverRenderDistance
                    + " awaiting_0x71");
            BedrockBridgeContext.LOG.info(
                    "BE YapGeyserSession connect+setServerRenderDistance " + username
                            + " proto=" + protocol
                            + " view=" + serverRenderDistance
                            + " bedrockRadius=" + ChunkUtils.squareToCircle(Math.max(0, serverRenderDistance))
                            + " — waiting SetLocalPlayerAsInitialized");
        } else {
            // Pre-Cloudburst proto: minimal hand-rolled spawn (rare).
            ctx.loginPhase.put(guid, BedrockBridgeContext.LoginPhase.AWAITING_CLIENT_INIT);
            int cx = spawnX >> 4;
            int cz = spawnZ >> 4;
            List<io.netty.buffer.ByteBuf> out = new ArrayList<>();
            out.add(BedrockPacketCodec.startGame(runtimeId, runtimeId, "YaPcore", spawnX, spawnY, spawnZ, uuid, protocol));
            out.add(BedrockPacketCodec.itemRegistry(protocol));
            out.add(BedrockWorldCodec.levelChunkEmpty(cx, cz, protocol));
            ctx.columns.markSent(guid, cx, cz);
            out.add(BedrockPacketCodec.biomeDefinitionListEmpty());
            out.add(BedrockPacketCodec.availableEntityIdentifiersEmpty());
            out.add(BedrockPacketCodec.creativeContentEmpty());
            out.add(BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.PLAYER_SPAWN));
            out.add(BedrockPacketCodec.setCommandsEnabled(true));
            out.add(BedrockPacketCodec.updateAttributesDefault(runtimeId));
            ctx.send(guid, out);
            BedrockBridgeContext.LOG.info(
                    "BE YapGeyserSession legacy spawn " + username + " proto=" + protocol);
        }

        Map<String, String> joinPayload = ctx.pendingJoin.remove(guid);
        if (joinPayload != null) {
            BedrockGameplayBridge.GameAction join =
                    new BedrockGameplayBridge.GameAction("JOIN", username, joinPayload);
            if (actions != null) {
                actions.add(join);
            } else {
                ctx.emitJoin.accept(join);
            }
        }
        ctx.pendingPack.remove(guid);
    }

    /**
     * Port of Geyser {@code GeyserSession.connect()}.
     */
    public void connect() {
        minY = OVERWORLD_MIN_Y;
        maxY = OVERWORLD_MAX_Y;
        if (CloudburstPackets.needsDimensionData(minY, maxY)) {
            sendUpstreamPacket(CloudburstPackets.dimensionDataOverworld());
        }

        YapGeyserSessionStartGame.startGame(this);
        sentSpawnPacket = true;
        YapGeyserSessionStartGame.syncEntityProperties(this);

        sendUpstreamPacket(CloudburstPackets.itemComponentFull(cloudburst));

        // Match StartGame spawn column (YaP has no Java teleport; see buildStartGamePacket).
        ChunkUtils.sendEmptyChunks(this, spawnBlockPos(), 0, false);
        ctx.columns.markSent(guid, spawnX >> 4, spawnZ >> 4);

        YapGeyserSessionStartGame.sendRegistryDefinitions(this);
        YapGeyserSessionStartGame.sendInitialPlayerState(this);
        YapGeyserSessionStartGame.sendInitialGameRules(this);
        resetTimeParameters();

        BedrockBridgeContext.LOG.info(
                "BE YapGeyserSession.connect complete user=" + username
                        + " spawn=" + spawnX + "," + spawnY + "," + spawnZ);
    }

    /** Port of Geyser {@code resetTimeParameters}. */
    public void resetTimeParameters() {
        setGameTicks(0L);
        setTimeTicks(0L, 0.0f);
        setClockRate(0.0f);
    }

    public void setGameTicks(long ticks) {
        // YaP join does not drive a Java tick counter yet.
    }

    public void setTimeTicks(long timeTicks, float partialTick) {
        this.dayTimeTicks = timeTicks;
        this.partialTimeTick = partialTick;
        synchronizeTime();
    }

    public void setClockRate(float rate) {
        this.clockRate = rate;
        this.shouldClientTickClock = this.clockRate == 1.0f;
        if (rate == 0.0f) {
            sendUpstreamPacket(CloudburstPackets.gameRuleDoDaylightCycle(false));
        }
    }

    public void synchronizeTime() {
        SetTimePacket setTimePacket = new SetTimePacket();
        setTimePacket.setTime((int) (Math.abs(dayTimeTicks) % (24000L * 8L)));
        sendUpstreamPacket(setTimePacket);
    }

    /**
     * Port of Geyser {@code setServerRenderDistance} → {@code recalculateBedrockRenderDistance}
     * (ChunkRadiusUpdated with {@link ChunkUtils#squareToCircle}).
     */
    public void setServerRenderDistance(int renderDistance) {
        renderDistance = Math.min(renderDistance, 96);
        this.serverRenderDistance = renderDistance;
        ctx.columns.setRadius(guid, Math.max(2, Math.min(32, renderDistance)));
        recalculateBedrockRenderDistance();
    }

    public void setClientRenderDistance(int clientRenderDistance) {
        // Store only — do NOT emit ChunkRadiusUpdated.
        // JOIN_PROBE 2026-09-10: RequestChunkRadius → setClientRenderDistance flipped
        // squareToCircle and sent a second 0x46 while still AWAITING_CLIENT_INIT; never 0x71.
        // BedrockRequestChunkRadiusTranslator is documented store-only (Geyser → Java settings).
        this.clientRenderDistance = clientRenderDistance;
        ctx.chunkRadius.put(guid, Math.max(2, Math.min(32, clientRenderDistance)));
    }

    private void recalculateBedrockRenderDistance() {
        int renderDistance = ChunkUtils.squareToCircle(this.serverRenderDistance);
        ChunkRadiusUpdatedPacket chunkRadiusUpdatedPacket = new ChunkRadiusUpdatedPacket();
        chunkRadiusUpdatedPacket.setRadius(renderDistance);
        sendUpstreamPacket(chunkRadiusUpdatedPacket);
    }

    private double[] paperSpawnOrDefault() {
        if (ctx.paperWorld != null && ctx.paperWorld.isEnabled()) {
            try {
                double[] s = ctx.paperWorld.spawnPosition();
                if (s != null && s.length >= 3) {
                    return s;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return new double[] {8.5, 65.0, -7.5};
    }
}

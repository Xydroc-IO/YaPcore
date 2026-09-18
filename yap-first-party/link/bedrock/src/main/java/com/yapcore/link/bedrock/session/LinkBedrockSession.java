package com.yapcore.link.bedrock.session;
import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.translator.JeToBedrockBlockMapper;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector2i;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
public final class LinkBedrockSession {
    static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    /** Default JE/Bedrock square view when Folia has not advertised one yet. */
    public static final int DEFAULT_JAVA_VIEW = 32;
    public static final int OVERWORLD_MIN_Y = -64;
    public static final int OVERWORLD_MAX_Y = 320;
    final long guid;
    final long runtimeId;
    final String username;
    final UUID uuid;
    final int protocol;
    final LinkCloudburstCodecs.Session codec;
    final Consumer<List<? extends BedrockPacket>> upstreamSink;
    final LinkBedrockSessionConnect connectLogic = new LinkBedrockSessionConnect(this);
    final LinkBedrockSessionSpawn spawnLogic = new LinkBedrockSessionSpawn(this);
    final LinkBedrockSessionPlay playLogic = new LinkBedrockSessionPlay(this);
    final LinkBedrockSessionChunks chunksLogic = new LinkBedrockSessionChunks(this);
    int spawnX = 8;
    int spawnY = 65;
    int spawnZ = -7;
    volatile double spawnFeetX = 8.5;
    volatile double spawnFeetY = 65.0;
    volatile double spawnFeetZ = -6.5;
    int serverRenderDistance = -1;
    int pendingFullRenderDistance = -1;
    int clientRenderDistance;
    Vector2i lastChunkPosition;
    boolean sentSpawnPacket;
    final AtomicBoolean playerSpawnSent = new AtomicBoolean(false);
    final AtomicBoolean spawnColumnReal = new AtomicBoolean(false);
    final AtomicBoolean spawnColumnSolid = new AtomicBoolean(false);
    final AtomicInteger spawnColumnSolidBlocks = new AtomicInteger(0);
    final AtomicBoolean playerSpawnTimeoutScheduled = new AtomicBoolean(false);
    final AtomicBoolean spawnFreezeScheduled = new AtomicBoolean(false);
    static final long PLAYER_SPAWN_REAL_TIMEOUT_MS = 10000L;
    static final int MIN_SPAWN_SOLID_NEAR_FEET = 4;
    static final int POST_SPAWN_GROUND_HOLD_TICKS = 10;
    boolean upstreamInitialized;
    volatile JoinPhase joinPhase = JoinPhase.NONE;
    final ConcurrentHashMap<Long, Boolean> sentColumns = new ConcurrentHashMap<>();
    volatile JavaDownstreamClient downstream;
    volatile JeToBedrockBlockMapper blockMapper;
    volatile int javaEntityId = -1;
    volatile int javaPermissionLevel;
    volatile int pendingJavaView = LinkBedrockSession.DEFAULT_JAVA_VIEW;
    volatile boolean awaitingJavaSpawn = true;
    final AtomicBoolean playerLoadedSent = new AtomicBoolean(false);
    final AtomicInteger realJeChunksSent = new AtomicInteger(0);
    final ConcurrentLinkedQueue<PendingJeChunk> pendingJeChunks = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<PendingJeChunk> pendingRealChunks = new ConcurrentLinkedQueue<>();
    static final int MAX_PENDING_JE_CHUNKS = 512;
    volatile int bedrockDimension = 0;
    public static final double PLAYER_EYE_OFFSET = 1.62;
    volatile double posX;
    volatile double posY = 65.0;
    volatile double posZ;
    volatile float yaw;
    volatile float pitch;
    volatile int pendingTeleportId = -1;
    volatile int lastAcceptedTeleportId = -1;
    volatile double lastSyncX = Double.NaN;
    volatile double lastSyncY = Double.NaN;
    volatile double lastSyncZ = Double.NaN;
    volatile int heldHotbar;
    volatile int lastAttackTarget;
    volatile int lastPlayerInputFlags = -1;
    final AtomicInteger blockSequence = new AtomicInteger(1);
    volatile int digX = Integer.MIN_VALUE;
    volatile int digY = Integer.MIN_VALUE;
    volatile int digZ = Integer.MIN_VALUE;
    volatile int digSequence = -1;
    final AtomicLong moveTick = new AtomicLong();
    final ConcurrentHashMap<Integer, Long> entityRuntimeByJava = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Integer, float[]> entityPosByJava = new ConcurrentHashMap<>();
    /** Java entity ids that are remote players (need eye-Y on MoveEntityAbsolute). */
    final java.util.Set<Integer> playerJavaEntityIds = ConcurrentHashMap.newKeySet();
    final ConcurrentHashMap<UUID, String> playerNamesByUuid = new ConcurrentHashMap<>();
    /** JE add_entity arrived before StartGame — flush after Bedrock can render. */
    final ConcurrentHashMap<Integer, PendingAddEntity> pendingAddEntities = new ConcurrentHashMap<>();
    final Set<String> pluginCommandNames = ConcurrentHashMap.newKeySet();
    volatile int lastJeInventorySlots = 46;
    volatile int lastJeWindowId = -1;
    volatile int lastJeMenuType = -1;
    volatile boolean inventoryOpen;
    volatile ItemData[] bedrockInventorySlots;
    volatile ItemData[] bedrockArmorSlots;
    volatile ItemData bedrockOffhand = ItemData.AIR;
    volatile boolean hasBedrockInventorySnapshot;
    volatile float lastHealth = 20.0F;
    volatile int lastFood = 20;
    volatile float lastSaturation = 5.0F;
    volatile ItemData cursorItem = ItemData.AIR;
    volatile int jeContainerStateId;
    volatile int stackNetworkIdSeq = 1;
    final ConcurrentHashMap<Integer, ItemData> openContainerSlots = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Long, Integer> blockRuntimeAtPos = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Integer, Float> entityHealthByJava = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Integer, Integer> entityMoveTicks = new ConcurrentHashMap<>();
    volatile int pendingSolidNear = -1;
    volatile int pendingSolidColumn = -1;
    volatile long pendingMapHits;
    volatile long pendingMapMisses;
    volatile double pendingStandOnFeetY = Double.NaN;
    volatile boolean spawnPlatformPlaced;
    volatile boolean standOnLiftActive;
    static final double TELEPORT_CONFIRM_XZ = 0.1;
    static final double TELEPORT_CONFIRM_Y = 0.1;
    static final int TELEPORT_RESEND_THRESHOLD = 20;
    volatile int unconfirmedAuthMoves;
    volatile int groundHoldTicksRemaining;
    private LinkBedrockSession(
            long guid,
            long runtimeId,
            String username,
            UUID uuid,
            int protocol,
            LinkCloudburstCodecs.Session codec,
            Consumer<List<? extends BedrockPacket>> upstreamSink) {
        this.guid = guid;
        this.runtimeId = runtimeId;
        this.username = username;
        this.uuid = uuid;
        this.protocol = protocol;
        this.codec = codec;
        this.upstreamSink = upstreamSink;
    }
    public static LinkBedrockSession open(
            long guid,
            long runtimeId,
            String username,
            UUID uuid,
            int protocol,
            LinkCloudburstCodecs.Session codec,
            Consumer<List<? extends BedrockPacket>> upstreamSink) { return new LinkBedrockSession(guid, runtimeId, username, uuid, protocol, codec, upstreamSink); }
    public long guid() { return this.guid; }
    public long runtimeId() { return this.runtimeId; }
    public String username() { return this.username; }
    public UUID uuid() { return this.uuid; }
    public int protocolVersion() { return this.protocol; }
    public LinkCloudburstCodecs.Session codec() { return this.codec; }
    public JoinPhase joinPhase() { return this.joinPhase; }
    public void setJoinPhase(JoinPhase phase) {
        this.joinPhase = phase;
        BedrockJoinProbe.notePhase(this.guid, phase.name());
    }
    public int getServerRenderDistance() { return this.serverRenderDistance; }
    public void setPendingFullRenderDistance(int view) {
        this.pendingFullRenderDistance = Math.max(2, Math.min(32, view));
    }
    public int pendingFullRenderDistance() { return this.pendingFullRenderDistance; }
    public int getClientRenderDistance() { return this.clientRenderDistance; }
    public void setClientRenderDistance(int clientRenderDistance) {
        this.clientRenderDistance = clientRenderDistance;
    }
    public Vector2i getLastChunkPosition() { return this.lastChunkPosition; }
    public void setLastChunkPosition(Vector2i pos) {
        this.lastChunkPosition = pos;
    }
    public boolean isSentSpawnPacket() { return this.sentSpawnPacket; }
    public boolean isUpstreamInitialized() { return this.upstreamInitialized; }
    public void setUpstreamInitialized(boolean initialized) {
        this.upstreamInitialized = initialized;
    }
    public int bedrockDimensionId() { return this.bedrockDimension; }
    public void setBedrockDimensionId(int dimensionId) {
        this.bedrockDimension = dimensionId;
    }
    public int bedrockDimensionHeight() { return 384; }
    public int spawnX() { return this.spawnX; }
    public int spawnY() { return this.spawnY; }
    public int spawnZ() { return this.spawnZ; }
    public Vector3i spawnBlockPos() { return Vector3i.from(this.spawnX, this.spawnY, this.spawnZ); }
    public double spawnFeetX() { return this.spawnFeetX; }
    public double spawnFeetY() { return this.spawnFeetY; }
    public double spawnFeetZ() { return this.spawnFeetZ; }
    public boolean isPlayerSpawnSent() { return this.playerSpawnSent.get(); }
    public boolean isSpawnColumnReal() { return this.spawnColumnReal.get(); }
    public boolean isSpawnColumnSolid() { return this.spawnColumnSolid.get(); }
    public int spawnColumnSolidBlocks() { return this.spawnColumnSolidBlocks.get(); }
    public void setSpawnFromFeet(double feetX, double feetY, double feetZ) {
        this.spawnFeetX = feetX;
        this.spawnFeetY = feetY;
        this.spawnFeetZ = feetZ;
        this.spawnX = (int) Math.floor(feetX);
        this.spawnY = (int) Math.floor(feetY);
        this.spawnZ = (int) Math.floor(feetZ);
        this.posX = feetX;
        this.posY = feetY;
        this.posZ = feetZ;
    }
    @Deprecated
    public void setSpawn(int x, int y, int z) {
        this.setSpawnFromFeet(x + 0.5, y, z + 0.5);
    }
    public JavaDownstreamClient downstream() { return this.downstream; }
    public void setDownstream(JavaDownstreamClient downstream) {
        this.downstream = downstream;
        if (downstream != null) {
            this.blockMapper = new JeToBedrockBlockMapper(this.protocol, downstream.blockRegistry());
        }
    }
    public JeToBedrockBlockMapper blockMapper() { return this.blockMapper; }
    public void setBlockMapper(JeToBedrockBlockMapper blockMapper) {
        this.blockMapper = blockMapper;
    }
    public int javaEntityId() { return this.javaEntityId; }
    public void setJavaEntityId(int javaEntityId) {
        this.javaEntityId = javaEntityId;
    }
    public int javaPermissionLevel() { return this.javaPermissionLevel; }
    public boolean setJavaPermissionLevel(int level) {
        int clamped = Math.max(0, Math.min(4, level));
        if (this.javaPermissionLevel == clamped) {
            return false;
        }
        this.javaPermissionLevel = clamped;
        return true;
    }
    public void setPosition(double x, double y, double z, float yaw, float pitch) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.moveTick.incrementAndGet();
    }
    public double posX() { return this.posX; }
    public double posY() { return this.posY; }
    public double posZ() { return this.posZ; }
    public float yaw() { return this.yaw; }
    public float pitch() { return this.pitch; }
    public long moveTick() { return this.moveTick.get(); }
    public long nextMoveTick() { return this.moveTick.incrementAndGet(); }
    public double lastSyncX() { return this.lastSyncX; }
    public double lastSyncY() { return this.lastSyncY; }
    public double lastSyncZ() { return this.lastSyncZ; }
    public void setPendingTeleportId(int teleportId) {
        this.pendingTeleportId = teleportId;
    }
    public int pendingTeleportId() { return this.pendingTeleportId; }
    public int lastAcceptedTeleportId() { return this.lastAcceptedTeleportId; }
    public void clearPendingTeleport() {
        this.pendingTeleportId = -1;
        this.unconfirmedAuthMoves = 0;
    }
    public int unconfirmedAuthMoves() { return this.unconfirmedAuthMoves; }
    public void setHeldHotbar(int slot) {
        this.heldHotbar = Math.max(0, Math.min(8, slot));
    }
    public int heldHotbar() { return this.heldHotbar; }
    public int nextBlockSequence() { return this.blockSequence.getAndIncrement(); }
    public boolean isDigging(int x, int y, int z) { return this.digSequence >= 0 && this.digX == x && this.digY == y && this.digZ == z; }
    public int lastAttackTarget() { return this.lastAttackTarget; }
    public int lastJeInventorySlots() { return this.lastJeInventorySlots; }
    public int lastJeWindowId() { return this.lastJeWindowId; }
    public int lastJeMenuType() { return this.lastJeMenuType; }
    public boolean inventoryOpen() { return this.inventoryOpen; }
    public void setInventoryOpen(boolean open) {
        this.inventoryOpen = open;
    }
    public boolean hasBedrockInventorySnapshot() { return this.hasBedrockInventorySnapshot; }
    public ItemData[] bedrockInventorySlots() { return this.bedrockInventorySlots; }
    public ItemData[] bedrockArmorSlots() { return this.bedrockArmorSlots; }
    public ItemData bedrockOffhand() { return this.bedrockOffhand; }
    public List<ItemDefinition> itemDefinitions() { return this.codec == null ? List.of() : this.codec.itemDefinitions(); }
    public float lastHealth() { return this.lastHealth; }
    public int lastFood() { return this.lastFood; }
    public float lastSaturation() { return this.lastSaturation; }
    public BlockDefinition stoneBlockDefinition() { return this.blockDefinitionOrAir(this.stoneRuntimeId()); }
    public BlockDefinition airBlockDefinition() { return this.blockDefinitionOrAir(this.airRuntimeId()); }
    public void sendUpstreamPacket(BedrockPacket packet) {
        if (packet != null && this.upstreamSink != null) {
            this.upstreamSink.accept(List.of(packet));
        }
    }
    public void sendUpstreamPackets(List<? extends BedrockPacket> packets) {
        if (packets != null && !packets.isEmpty() && this.upstreamSink != null) {
            this.upstreamSink.accept(packets);
        }
    }
    public boolean wasColumnSent(int chunkX, int chunkZ) { return this.sentColumns.containsKey(LinkBedrockSessionConnect.columnKey(chunkX, chunkZ)); }
    public void markColumnSent(int chunkX, int chunkZ) {
        this.sentColumns.put(LinkBedrockSessionConnect.columnKey(chunkX, chunkZ), Boolean.TRUE);
    }

    /**
     * Forget Bedrock column / publisher state after JE respawn (portal / dim change).
     * Without this, {@link com.yapcore.link.bedrock.translator.ChunkUtils#updateChunkPosition}
     * may skip NetworkChunkPublisherUpdate when chunk XZ coincides, and empty-seed marks
     * from the previous dimension linger.
     */
    public void clearWorldForDimensionChange() {
        this.sentColumns.clear();
        this.lastChunkPosition = null;
    }
    public enum JoinPhase {
        NONE,
        AWAITING_JAVA_LOGIN,
        AWAITING_CLIENT_INIT,
        SPAWNED
    }
    record PendingJeChunk(int chunkX, int chunkZ, ByteBuf payload) {}
    @FunctionalInterface
    public interface PendingChunkConsumer {
        void accept(int chunkX, int chunkZ, ByteBuf payload);
    }
    public void connect() {
        connectLogic.connect();
    }
    public void tryCompletePlayerSpawn(String reason) {
        connectLogic.tryCompletePlayerSpawn(reason);
    }
    public boolean consumePostSpawnGroundHold(double feetX, double feetY, double feetZ, boolean onGround) { return connectLogic.consumePostSpawnGroundHold(feetX, feetY, feetZ, onGround); }
    public void resetPostSpawnGroundHold() {
        connectLogic.resetPostSpawnGroundHold();
    }
    public void setServerRenderDistance(int renderDistance) {
        connectLogic.setServerRenderDistance(renderDistance);
    }
    public void onJavaLoginPlay(JavaDownstreamClient.LoginPlayInfo info) {
        connectLogic.onJavaLoginPlay(info);
    }
    public void onJavaRespawn(JavaDownstreamClient.RespawnInfo info) {
        connectLogic.onJavaRespawn(info);
    }
    public void onJavaSpawnPosition(double x, double y, double z) {
        connectLogic.onJavaSpawnPosition(x, y, z);
    }
    public void bufferOrTranslateLevelChunk(int chunkX, int chunkZ, ByteBuf payload) {
        connectLogic.bufferOrTranslateLevelChunk(chunkX, chunkZ, payload);
    }
    public void bufferPendingRealChunk(int chunkX, int chunkZ, ByteBuf payload) {
        connectLogic.bufferPendingRealChunk(chunkX, chunkZ, payload);
    }
    public int drainPendingRealChunks(PendingChunkConsumer consumer) { return connectLogic.drainPendingRealChunks(consumer); }
    public void noteRealJeChunkSent() {
        connectLogic.noteRealJeChunkSent();
    }
    public int realJeChunksSent() { return connectLogic.realJeChunksSent(); }
    public void closeFromBedrock(String reason) {
        connectLogic.closeFromBedrock(reason);
    }
    public void sendPlayerLoadedOnce() {
        connectLogic.sendPlayerLoadedOnce();
    }
    public void pendingSpawnSolidSample(int nearFeet, int columnNonAir, long mapHits, long mapMisses) {
        spawnLogic.pendingSpawnSolidSample(nearFeet, columnNonAir, mapHits, mapMisses);
    }
    public void pendingSpawnSolidSample(
            int nearFeet, int columnNonAir, long mapHits, long mapMisses, double standOnFeetY) {
        spawnLogic.pendingSpawnSolidSample(nearFeet, columnNonAir, mapHits, mapMisses, standOnFeetY);
    }
    public void flushPendingSpawnSolidSample() {
        spawnLogic.flushPendingSpawnSolidSample();
    }
    public void applyStandOnFeetIfNeeded(double standOnFeetY, boolean platform) {
        spawnLogic.applyStandOnFeetIfNeeded(standOnFeetY, platform);
    }
    public void applyStandOnFeetIfNeeded(double standOnFeetY, boolean platform, int solidNearFeet) {
        spawnLogic.applyStandOnFeetIfNeeded(standOnFeetY, platform, solidNearFeet);
    }
    public boolean isStandOnLiftActive() { return spawnLogic.isStandOnLiftActive(); }
    public boolean shouldRejectBuriedJeFeet(double jeFeetY) { return spawnLogic.shouldRejectBuriedJeFeet(jeFeetY); }
    public void clearStandOnLift(String reason) {
        spawnLogic.clearStandOnLift(reason);
    }
    public void punchStandOnAirCells() {
        spawnLogic.punchStandOnAirCells();
    }
    public void placeStandOnCollisionPlatform() {
        spawnLogic.placeStandOnCollisionPlatform();
    }
    public void noteSpawnColumnReal(int chunkX, int chunkZ) {
        spawnLogic.noteSpawnColumnReal(chunkX, chunkZ);
    }
    public void noteSpawnColumnSolid(int solidBlocksNearFeet) {
        spawnLogic.noteSpawnColumnSolid(solidBlocksNearFeet);
    }
    public void armPostInitPositionConfirm(double feetX, double feetY, double feetZ) {
        playLogic.armPostInitPositionConfirm(feetX, feetY, feetZ);
    }
    public boolean markTeleportAccepted(int teleportId, double x, double y, double z) { return playLogic.markTeleportAccepted(teleportId, x, y, z); }
    public boolean isNearLastSync(double x, double y, double z) { return playLogic.isNearLastSync(x, y, z); }
    public boolean confirmOrHoldAuthMove(double feetX, double feetY, double feetZ) { return playLogic.confirmOrHoldAuthMove(feetX, feetY, feetZ); }
    public boolean canConfirmTeleport(double feetX, double feetY, double feetZ) { return playLogic.canConfirmTeleport(feetX, feetY, feetZ); }
    public boolean shouldResendTeleport() { return playLogic.shouldResendTeleport(); }
    public void resetUnconfirmedAuthMoves() {
        playLogic.resetUnconfirmedAuthMoves();
    }
    public int beginOrContinueDig(int x, int y, int z) { return playLogic.beginOrContinueDig(x, y, z); }
    public void clearDig() {
        playLogic.clearDig();
    }
    public void setLastAttackTarget(int entityId) {
        playLogic.setLastAttackTarget(entityId);
    }
    public void noteAttackTarget(int entityId) {
        playLogic.noteAttackTarget(entityId);
    }
    public boolean notePlayerInputFlags(int flags) { return playLogic.notePlayerInputFlags(flags); }
    public void trackEntity(int javaEntityId, long runtimeId) {
        playLogic.trackEntity(javaEntityId, runtimeId);
    }

    public void markPlayerJavaEntity(int javaEntityId) {
        if (javaEntityId != javaEntityId()) {
            playerJavaEntityIds.add(javaEntityId);
        }
    }

    public boolean isPlayerJavaEntity(int javaEntityId) {
        return playerJavaEntityIds.contains(javaEntityId);
    }

    public Long runtimeForJava(int javaEntityId) { return playLogic.runtimeForJava(javaEntityId); }
    public int javaEntityForRuntime(long runtimeId) { return playLogic.javaEntityForRuntime(runtimeId); }
    public void setEntityPos(int javaEntityId, float x, float y, float z, float yaw, float pitch) {
        playLogic.setEntityPos(javaEntityId, x, y, z, yaw, pitch);
    }
    public float[] entityPos(int javaEntityId) { return playLogic.entityPos(javaEntityId); }
    public Long untrackEntity(int javaEntityId) { return playLogic.untrackEntity(javaEntityId); }
    public void rememberPlayerName(UUID id, String name) {
        playLogic.rememberPlayerName(id, name);
    }
    public void forgetPlayerName(UUID id) {
        playLogic.forgetPlayerName(id);
    }
    public String playerName(UUID id) { return playLogic.playerName(id); }
    public Map<UUID, String> playerNameSnapshot() { return playLogic.playerNameSnapshot(); }

    public void bufferPendingAddEntity(int entityId, UUID uuid, String typeKey,
                                       double x, double y, double z, float yaw, float pitch) {
        if (entityId == javaEntityId) {
            return;
        }
        pendingAddEntities.put(entityId, new PendingAddEntity(
                entityId, uuid, typeKey, x, y, z, yaw, pitch));
        BedrockJoinProbe.noteEvent(guid, "pending_add_entity id=" + entityId
                + " type=" + typeKey + " buffered=" + pendingAddEntities.size());
    }

    public void flushPendingAddEntities() {
        if (!sentSpawnPacket || pendingAddEntities.isEmpty()) {
            return;
        }
        java.util.ArrayList<PendingAddEntity> batch = new java.util.ArrayList<>(pendingAddEntities.values());
        pendingAddEntities.clear();
        LOG.info("BE flush pending add_entity count=" + batch.size() + " user=" + username);
        BedrockJoinProbe.noteEvent(guid, "flush_pending_add_entity count=" + batch.size());
        for (PendingAddEntity p : batch) {
            com.yapcore.link.bedrock.translator.JavaEntityTranslator.onAddEntity(
                    this, p.entityId(), p.uuid(), p.typeKey(),
                    p.x(), p.y(), p.z(), p.yaw(), p.pitch());
        }
    }

    public record PendingAddEntity(int entityId, UUID uuid, String typeKey,
                                   double x, double y, double z, float yaw, float pitch) {
    }

    public void rememberPluginCommands(Iterable<String> names) {
        playLogic.rememberPluginCommands(names);
    }
    public Set<String> pluginCommandNames() { return playLogic.pluginCommandNames(); }
    public void rememberJeInventorySlots(int slots) {
        playLogic.rememberJeInventorySlots(slots);
    }
    public void rememberJeWindow(int windowId, int menuType) {
        playLogic.rememberJeWindow(windowId, menuType);
    }
    public void storeBedrockInventory(ItemData[] inv, ItemData[] armor, ItemData offhand) {
        playLogic.storeBedrockInventory(inv, armor, offhand);
    }
    public void rememberHealth(float health, int food, float saturation) {
        playLogic.rememberHealth(health, food, saturation);
    }
    public ItemData cursorItem() { return playLogic.cursorItem(); }
    public void setCursorItem(ItemData item) { playLogic.setCursorItem(item); }
    public int jeContainerStateId() { return playLogic.jeContainerStateId(); }
    public void rememberJeContainerState(int stateId) { playLogic.rememberJeContainerState(stateId); }
    public int nextStackNetworkId() { return playLogic.nextStackNetworkId(); }
    public ItemData containerSlot(int slot) { return playLogic.containerSlot(slot); }
    public void setContainerSlot(int slot, ItemData item) { playLogic.setContainerSlot(slot, item); }
    public void clearOpenContainerSlots() { playLogic.clearOpenContainerSlots(); }
    public void rememberBlockRuntime(int x, int y, int z, int runtimeId) {
        playLogic.rememberBlockRuntime(x, y, z, runtimeId);
    }
    public int blockRuntimeAt(int x, int y, int z) { return playLogic.blockRuntimeAt(x, y, z); }
    public void rememberEntityHealth(int javaEntityId, float health) {
        playLogic.rememberEntityHealth(javaEntityId, health);
    }
    public Float entityHealth(int javaEntityId) { return playLogic.entityHealth(javaEntityId); }
    public boolean bumpEntityMoveForceAbsolute(int javaEntityId) { return playLogic.bumpEntityMoveForceAbsolute(javaEntityId); }
    public BlockDefinition blockDefinitionOrAir(int runtimeIdHint) { return playLogic.blockDefinitionOrAir(runtimeIdHint); }
    public int airRuntimeId() { return playLogic.airRuntimeId(); }
    public int stoneRuntimeId() { return playLogic.stoneRuntimeId(); }
}

package com.yapcore.link.bedrock.session;
import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
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
    final LinkBedrockSessionSwitch switchLogic = new LinkBedrockSessionSwitch(this);
    final LinkBedrockSessionPose poseLogic = new LinkBedrockSessionPose(this);
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
    /** True while YaPPortals Connect is soft-switching the JE downstream (no TransferPacket). */
    volatile boolean softBackendSwitch;
    volatile String softBackendSwitchTarget;
    /** Until this epoch ms, auth HOLD is bypassed so portal arrival TPs apply. */
    volatile long softSwitchMoveGraceUntilMs;
    /** Proxy Connect (/hub, /server) — host soft-switches JE backend. */
    volatile Consumer<String> backendSwitchHandler;
    final AtomicBoolean playerSpawnSent = new AtomicBoolean(false);
    final AtomicBoolean joinInitAssistScheduled = new AtomicBoolean(false);
    final AtomicBoolean postInitViewExpanded = new AtomicBoolean(false);
    final AtomicBoolean softPlayableViewExpanded = new AtomicBoolean(false);
    final AtomicBoolean spawnColumnReal = new AtomicBoolean(false);
    final AtomicBoolean spawnColumnSolid = new AtomicBoolean(false);
    final AtomicInteger spawnColumnSolidBlocks = new AtomicInteger(0);
    final AtomicBoolean playerSpawnTimeoutScheduled = new AtomicBoolean(false);
    final AtomicBoolean spawnFreezeScheduled = new AtomicBoolean(false);
    static final long PLAYER_SPAWN_REAL_TIMEOUT_MS = 3000L;
    static final int MIN_SPAWN_SOLID_NEAR_FEET = 4;
    static final int POST_SPAWN_GROUND_HOLD_TICKS = 10;
    boolean upstreamInitialized;
    volatile JoinPhase joinPhase = JoinPhase.NONE;
    final ConcurrentHashMap<Long, Boolean> sentColumns = new ConcurrentHashMap<>();
    /** Columns that received a REAL (non-empty) JE→BE LevelChunk — never blank these. */
    final ConcurrentHashMap<Long, Boolean> realColumns = new ConcurrentHashMap<>();
    volatile JavaDownstreamClient downstream;
    volatile JeToBedrockBlockMapper blockMapper;
    volatile int javaEntityId = -1;
    volatile int javaPermissionLevel;
    volatile int pendingJavaView = LinkBedrockSession.DEFAULT_JAVA_VIEW;
    volatile boolean awaitingJavaSpawn = true;
    final AtomicBoolean playerLoadedSent = new AtomicBoolean(false);
    final AtomicBoolean joinSquareNudgeSent = new AtomicBoolean(false);
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
    final ConcurrentHashMap<UUID, Integer> playerEntityByUuid = new ConcurrentHashMap<>();
    /** JE add_entity arrived before StartGame — flush after Bedrock can render. */
    final ConcurrentHashMap<Integer, LinkBedrockSessionPending.PendingAddEntity> pendingAddEntities = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Integer, String> pendingCustomNames = new ConcurrentHashMap<>();
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
    static final double TELEPORT_CONFIRM_XZ = 1.5;
    static final double TELEPORT_CONFIRM_Y = 2.0;
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
    public void setJoinPhase(JoinPhase phase) { poseLogic.setJoinPhase(phase); }
    public int getServerRenderDistance() { return this.serverRenderDistance; }
    public void setPendingFullRenderDistance(int view) { poseLogic.setPendingFullRenderDistance(view); }
    public int pendingFullRenderDistance() { return this.pendingFullRenderDistance; }
    public int getClientRenderDistance() { return this.clientRenderDistance; }
    public void setClientRenderDistance(int clientRenderDistance) { poseLogic.setClientRenderDistance(clientRenderDistance); }
    public Vector2i getLastChunkPosition() { return this.lastChunkPosition; }
    public void setLastChunkPosition(Vector2i pos) { poseLogic.setLastChunkPosition(pos); }
    public boolean isSentSpawnPacket() { return this.sentSpawnPacket; }
    public boolean isUpstreamInitialized() { return this.upstreamInitialized; }
    public void setUpstreamInitialized(boolean initialized) { poseLogic.setUpstreamInitialized(initialized); }
    public int bedrockDimensionId() { return this.bedrockDimension; }
    public void setBedrockDimensionId(int dimensionId) { poseLogic.setBedrockDimensionId(dimensionId); }
    public int bedrockDimensionHeight() { return 384; }
    public int spawnX() { return this.spawnX; }
    public int spawnY() { return this.spawnY; }
    public int spawnZ() { return this.spawnZ; }
    public Vector3i spawnBlockPos() { return poseLogic.spawnBlockPos(); }
    public double spawnFeetX() { return this.spawnFeetX; }
    public double spawnFeetY() { return this.spawnFeetY; }
    public double spawnFeetZ() { return this.spawnFeetZ; }
    public boolean isPlayerSpawnSent() { return this.playerSpawnSent.get(); }
    public boolean isSpawnColumnReal() { return this.spawnColumnReal.get(); }
    public boolean isSpawnColumnSolid() { return this.spawnColumnSolid.get(); }
    public int spawnColumnSolidBlocks() { return this.spawnColumnSolidBlocks.get(); }
    public void setSpawnFromFeet(double feetX, double feetY, double feetZ) { poseLogic.setSpawnFromFeet(feetX, feetY, feetZ); }
    @Deprecated
    public void setSpawn(int x, int y, int z) { poseLogic.setSpawn(x, y, z); }
    public JavaDownstreamClient downstream() { return this.downstream; }
    public void setDownstream(JavaDownstreamClient downstream) { poseLogic.setDownstream(downstream); }
    public JeToBedrockBlockMapper blockMapper() { return this.blockMapper; }
    public void setBlockMapper(JeToBedrockBlockMapper blockMapper) { poseLogic.setBlockMapper(blockMapper); }
    public int javaEntityId() { return this.javaEntityId; }
    public void setJavaEntityId(int javaEntityId) { poseLogic.setJavaEntityId(javaEntityId); }
    public int javaPermissionLevel() { return this.javaPermissionLevel; }
    public boolean setJavaPermissionLevel(int level) { return poseLogic.setJavaPermissionLevel(level); }
    public void setPosition(double x, double y, double z, float yaw, float pitch) { poseLogic.setPosition(x, y, z, yaw, pitch); }
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
    public void setPendingTeleportId(int teleportId) { poseLogic.setPendingTeleportId(teleportId); }
    public int pendingTeleportId() { return this.pendingTeleportId; }
    public int lastAcceptedTeleportId() { return this.lastAcceptedTeleportId; }
    public void clearPendingTeleport() { poseLogic.clearPendingTeleport(); }
    public int unconfirmedAuthMoves() { return this.unconfirmedAuthMoves; }
    public void setHeldHotbar(int slot) { poseLogic.setHeldHotbar(slot); }
    public int heldHotbar() { return this.heldHotbar; }
    public int nextBlockSequence() { return this.blockSequence.getAndIncrement(); }
    public boolean isDigging(int x, int y, int z) { return poseLogic.isDigging(x, y, z); }
    public int lastAttackTarget() { return this.lastAttackTarget; }
    public int lastJeInventorySlots() { return this.lastJeInventorySlots; }
    public int lastJeWindowId() { return this.lastJeWindowId; }
    public int lastJeMenuType() { return this.lastJeMenuType; }
    public boolean inventoryOpen() { return this.inventoryOpen; }
    public void setInventoryOpen(boolean open) { poseLogic.setInventoryOpen(open); }
    public boolean hasBedrockInventorySnapshot() { return this.hasBedrockInventorySnapshot; }
    public ItemData[] bedrockInventorySlots() { return this.bedrockInventorySlots; }
    public ItemData[] bedrockArmorSlots() { return this.bedrockArmorSlots; }
    public ItemData bedrockOffhand() { return this.bedrockOffhand; }
    public List<ItemDefinition> itemDefinitions() { return poseLogic.itemDefinitions(); }
    public float lastHealth() { return this.lastHealth; }
    public int lastFood() { return this.lastFood; }
    public float lastSaturation() { return this.lastSaturation; }
    public BlockDefinition stoneBlockDefinition() { return poseLogic.stoneBlockDefinition(); }
    public BlockDefinition airBlockDefinition() { return poseLogic.airBlockDefinition(); }
    public void sendUpstreamPacket(BedrockPacket packet) { poseLogic.sendUpstreamPacket(packet); }
    public void sendUpstreamPackets(List<? extends BedrockPacket> packets) { poseLogic.sendUpstreamPackets(packets); }
    public boolean wasColumnSent(int chunkX, int chunkZ) { return poseLogic.wasColumnSent(chunkX, chunkZ); }
    public void markColumnSent(int chunkX, int chunkZ) { poseLogic.markColumnSent(chunkX, chunkZ); }
    public boolean wasRealColumnSent(int chunkX, int chunkZ) { return chunksLogic.wasRealColumnSent(chunkX, chunkZ); }
    public void markRealColumnSent(int chunkX, int chunkZ) { chunksLogic.markRealColumnSent(chunkX, chunkZ); }
    /** First caller wins — join-square-filled ChunkRadius/publisher nudge. */
    public boolean markJoinSquareNudgeSent() { return poseLogic.markJoinSquareNudgeSent(); }
    /** First caller wins — post-0x71 (or late) full-view expand. */
    public boolean markPostInitViewExpanded() { return poseLogic.markPostInitViewExpanded(); }
    public boolean isPostInitViewExpanded() { return poseLogic.isPostInitViewExpanded(); }
    /** First caller wins — soft-SPAWN mid-view (client slider 8–12) before real 0x71. */
    public boolean markSoftPlayableViewExpanded() { return poseLogic.markSoftPlayableViewExpanded(); }
    /** Forget column marks after JE respawn / dim change so publisher + REAL tracking reset. */
    public void clearWorldForDimensionChange() { chunksLogic.clearWorldForDimensionChange(); }

    public void beginSoftBackendSwitch(String targetServer) { switchLogic.beginSoftBackendSwitch(targetServer); }
    public void setBackendSwitchHandler(Consumer<String> handler) { switchLogic.setBackendSwitchHandler(handler); }
    public boolean requestBackendSwitch(String targetServer) { return switchLogic.requestBackendSwitch(targetServer); }
    /** Drop every remote actor so the soft-switched backend can re-Add them. */
    public void removeAllRemoteEntities() { switchLogic.removeAllRemoteEntities(); }
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
    public void rearmPlayerSpawnAfterJoinSquare() {
        connectLogic.rearmPlayerSpawnAfterJoinSquare();
    }
    public void scheduleJoinInitAssist() {
        connectLogic.scheduleJoinInitAssist();
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
        switchLogic.markPlayerJavaEntity(javaEntityId);
    }

    public boolean isPlayerJavaEntity(int javaEntityId) {
        return switchLogic.isPlayerJavaEntity(javaEntityId);
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
    public void rememberPlayerEntityUuid(UUID id, int javaEntityId) {
        switchLogic.rememberPlayerEntityUuid(id, javaEntityId);
    }
    public Integer javaEntityForPlayerUuid(UUID id) {
        return switchLogic.javaEntityForPlayerUuid(id);
    }
    public void forgetPlayerName(UUID id) {
        playLogic.forgetPlayerName(id);
    }
    public String playerName(UUID id) { return playLogic.playerName(id); }
    public Map<UUID, String> playerNameSnapshot() { return playLogic.playerNameSnapshot(); }

    public void bufferPendingAddEntity(int entityId, UUID uuid, String typeKey,
                                       double x, double y, double z, float yaw, float pitch) {
        switchLogic.bufferPendingAddEntity(entityId, uuid, typeKey, x, y, z, yaw, pitch);
    }

    public void rememberPendingCustomName(int entityId, String name, boolean visible) {
        switchLogic.rememberPendingCustomName(entityId, name, visible);
    }
    public String takePendingCustomName(int entityId) {
        return switchLogic.takePendingCustomName(entityId);
    }
    public void dropPendingAddEntity(int entityId) {
        switchLogic.dropPendingAddEntity(entityId);
    }
    public void updatePendingAddEntityPos(int entityId, double x, double y, double z,
                                          float yaw, float pitch) {
        switchLogic.updatePendingAddEntityPos(entityId, x, y, z, yaw, pitch);
    }
    public void flushPendingAddEntities() {
        switchLogic.flushPendingAddEntities();
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

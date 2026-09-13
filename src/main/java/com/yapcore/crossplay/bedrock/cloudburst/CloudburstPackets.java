package com.yapcore.crossplay.bedrock.cloudburst;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetCommandsEnabledPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetDifficultyPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetSpawnPositionPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTimePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.SyncEntityPropertyPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.VoxelShapesPacket;

/**
 * Static factories returning Cloudburst {@link BedrockPacket} instances (not ByteBuf).
 *
 * <p>Implementations live in package-private helpers
 * ({@link CloudburstPacketsLogin}, {@link CloudburstPacketsWorld},
 * {@link CloudburstPacketsInventory}) for the ≤500-line domain gate.
 */
public final class CloudburstPackets {

    private CloudburstPackets() {}

    public static VoxelShapesPacket voxelShapesEmpty() {
        return CloudburstPacketsLogin.voxelShapesEmpty();
    }

    public static PlayStatusPacket playStatusLoginSuccess() {
        return CloudburstPacketsLogin.playStatusLoginSuccess();
    }

    public static ServerToClientHandshakePacket serverToClientHandshake(String jwt) {
        return CloudburstPacketsLogin.serverToClientHandshake(jwt);
    }

    public static PlayStatusPacket playerSpawn() {
        return CloudburstPacketsLogin.playerSpawn();
    }

    public static NetworkSettingsPacket networkSettingsGeyser() {
        return CloudburstPacketsLogin.networkSettingsGeyser();
    }

    public static StartGamePacket startGame(long uniqueId, long runtimeId, String levelName,
                                            int blockX, int blockY, int blockZ, UUID worldId,
                                            CloudburstSession session) {
        return CloudburstPacketsLogin.startGame(
                uniqueId, runtimeId, levelName, blockX, blockY, blockZ, worldId, session);
    }

    public static ItemComponentPacket itemComponentFull(CloudburstSession session) {
        return CloudburstPacketsLogin.itemComponentFull(session);
    }

    public static LevelChunkPacket levelChunkEmpty(int chunkX, int chunkZ) {
        return CloudburstPacketsWorld.levelChunkEmpty(chunkX, chunkZ);
    }

    public static PlayerListPacket playerListAddSelf(UUID uuid, long entityUniqueId, String username) {
        return CloudburstPacketsWorld.playerListAddSelf(uuid, entityUniqueId, username);
    }

    public static DimensionDataPacket dimensionDataOverworld() {
        return CloudburstPacketsLogin.dimensionDataOverworld();
    }

    public static boolean needsDimensionData(int minY, int maxY) {
        return CloudburstPacketsLogin.needsDimensionData(minY, maxY);
    }

    @Deprecated
    public static BiomeDefinitionListPacket biomeDefinitionListEmpty() {
        return CloudburstPacketsLogin.biomeDefinitionListEmpty();
    }

    public static BiomeDefinitionListPacket biomeDefinitionListVanilla() {
        return CloudburstPacketsLogin.biomeDefinitionListVanilla();
    }

    public static ResourcePacksInfoPacket resourcePacksInfoEmpty() {
        return CloudburstPacketsLogin.resourcePacksInfoEmpty();
    }

    public static ResourcePackStackPacket resourcePackStackEmpty() {
        return CloudburstPacketsLogin.resourcePackStackEmpty();
    }

    public static List<SyncEntityPropertyPacket> syncEntityProperties() {
        return CloudburstPacketsWorld.syncEntityProperties();
    }

    @Deprecated
    public static SyncEntityPropertyPacket syncEntityPropertyEmpty() {
        return CloudburstPacketsWorld.syncEntityPropertyEmpty();
    }

    public static GameRulesChangedPacket gameRulesChangedInitial() {
        return CloudburstPacketsWorld.gameRulesChangedInitial();
    }

    public static GameRulesChangedPacket gameRuleDoDaylightCycle(boolean enabled) {
        return CloudburstPacketsWorld.gameRuleDoDaylightCycle(enabled);
    }

    public static CraftingDataPacket craftingDataClean() {
        return CloudburstPacketsWorld.craftingDataClean();
    }

    public static SetSpawnPositionPacket setSpawnPositionWorld(int blockX, int blockY, int blockZ) {
        return CloudburstPacketsWorld.setSpawnPositionWorld(blockX, blockY, blockZ);
    }

    public static List<BedrockPacket> respawnReadyAndMove(
            long runtimeId, float x, float y, float z, float pitch, float yaw) {
        return CloudburstPacketsWorld.respawnReadyAndMove(runtimeId, x, y, z, pitch, yaw);
    }

    public static AvailableCommandsPacket availableCommandsEmpty() {
        return CloudburstPacketsWorld.availableCommandsEmpty();
    }

    public static SetTimePacket setTime(int time) {
        return CloudburstPacketsWorld.setTime(time);
    }

    public static SetDifficultyPacket setDifficulty(int difficulty) {
        return CloudburstPacketsWorld.setDifficulty(difficulty);
    }

    public static AvailableEntityIdentifiersPacket availableEntityIdentifiers() {
        return CloudburstPacketsWorld.availableEntityIdentifiers();
    }

    @Deprecated
    public static AvailableEntityIdentifiersPacket availableEntityIdentifiersEmpty() {
        return CloudburstPacketsWorld.availableEntityIdentifiersEmpty();
    }

    public static CameraPresetsPacket cameraPresetsVanilla() {
        return CloudburstPacketsWorld.cameraPresetsVanilla();
    }

    @Deprecated
    public static CreativeContentPacket creativeContentEmpty() {
        return CloudburstPacketsInventory.creativeContentEmpty();
    }

    public static CreativeContentPacket creativeContentFull(CloudburstSession session) {
        return CloudburstPacketsInventory.creativeContentFull(session);
    }

    public static int squareToCircle(int chunkRadius) {
        return CloudburstPacketsWorld.squareToCircle(chunkRadius);
    }

    public static List<BedrockPacket> adventureSettingsSurvival(long uniqueEntityId) {
        return CloudburstPacketsWorld.adventureSettingsSurvival(uniqueEntityId);
    }

    public static List<BedrockPacket> adventureSettingsSurvival(
            long uniqueEntityId, float walkSpeed, float flySpeed) {
        return CloudburstPacketsWorld.adventureSettingsSurvival(uniqueEntityId, walkSpeed, flySpeed);
    }

    public static SetPlayerGameTypePacket setPlayerGameTypeSurvival() {
        return CloudburstPacketsWorld.setPlayerGameTypeSurvival();
    }

    public static List<InventoryContentPacket> inventoryContentPlayerEmpty() {
        return CloudburstPacketsInventory.inventoryContentPlayerEmpty();
    }

    public static UpdateAttributesPacket updateAttributesMovementOnly(long runtimeId) {
        return CloudburstPacketsInventory.updateAttributesMovementOnly(runtimeId);
    }

    public static UpdateAttributesPacket updateAttributesMovementOnly(long runtimeId, float movementSpeed) {
        return CloudburstPacketsInventory.updateAttributesMovementOnly(runtimeId, movementSpeed);
    }

    public static UpdateAttributesPacket updateAttributesDefault(long runtimeId) {
        return CloudburstPacketsInventory.updateAttributesDefault(runtimeId);
    }

    public static UpdateAttributesPacket updateAttributesDefault(long runtimeId, float movementSpeed) {
        return CloudburstPacketsInventory.updateAttributesDefault(runtimeId, movementSpeed);
    }

    public static SetCommandsEnabledPacket setCommandsEnabled(boolean enabled) {
        return CloudburstPacketsWorld.setCommandsEnabled(enabled);
    }

    public static ChunkRadiusUpdatedPacket chunkRadiusUpdatedFromJavaView(int javaViewDistance) {
        return CloudburstPacketsWorld.chunkRadiusUpdatedFromJavaView(javaViewDistance);
    }

    public static ChunkRadiusUpdatedPacket chunkRadiusUpdated(int bedrockCircleRadius) {
        return CloudburstPacketsWorld.chunkRadiusUpdated(bedrockCircleRadius);
    }

    public static NetworkChunkPublisherUpdatePacket networkChunkPublisherUpdate(
            int blockX, int blockY, int blockZ, int radiusBlocks) {
        return CloudburstPacketsWorld.networkChunkPublisherUpdate(blockX, blockY, blockZ, radiusBlocks);
    }

    public static TextPacket textChat(String source, String message) {
        return CloudburstPacketsWorld.textChat(source, message);
    }

    public static UpdateBlockPacket updateBlock(int x, int y, int z, int runtimeId,
                                                 CloudburstSession session) {
        return CloudburstPacketsWorld.updateBlock(x, y, z, runtimeId, session);
    }

    public static InventoryContentPacket inventoryContentEmpty(int windowId, int size) {
        return CloudburstPacketsInventory.inventoryContentEmpty(windowId, size);
    }

    public static MovePlayerPacket movePlayer(long runtimeId, float x, float y, float z,
                                               float pitch, float yaw, float headYaw,
                                               MovePlayerPacket.Mode mode, boolean onGround) {
        return CloudburstPacketsWorld.movePlayer(
                runtimeId, x, y, z, pitch, yaw, headYaw, mode, onGround);
    }

    public static AddPlayerPacket addPlayer(UUID uuid, String username, long runtimeId,
                                            float x, float y, float z, float yaw, float pitch) {
        return CloudburstPacketsWorld.addPlayer(uuid, username, runtimeId, x, y, z, yaw, pitch);
    }

    /** Package-private for same-package tests; implementation in {@link CloudburstPacketsWorld}. */
    static ByteBuf emptyBiomePayload() {
        return CloudburstPacketsWorld.emptyBiomePayload();
    }

    static ByteBuf emptyBiomePayload(int sections) {
        return CloudburstPacketsWorld.emptyBiomePayload(sections);
    }
}

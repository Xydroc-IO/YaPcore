package com.yapcore.crossplay.bedrock;

import com.yapcore.crossplay.bedrock.codec.*;
import io.netty.buffer.ByteBuf;

public final class BedrockPacketCodec {

    public static final int ID_LOGIN = 0x01;
    public static final int ID_PLAY_STATUS = 0x02;
    public static final int ID_SERVER_TO_CLIENT_HANDSHAKE = 0x03;
    public static final int ID_CLIENT_TO_SERVER_HANDSHAKE = 0x04;
    public static final int ID_DISCONNECT = 0x05;
    public static final int ID_RESOURCE_PACKS_INFO = 0x06;
    public static final int ID_RESOURCE_PACK_STACK = 0x07;
    public static final int ID_RESOURCE_PACK_CLIENT_RESPONSE = 0x08;
    public static final int ID_TEXT = 0x09;
    public static final int ID_START_GAME = 0x0b;
    public static final int ID_ADD_PLAYER = 0x0c;
    public static final int ID_REMOVE_ENTITY = 0x0e;
    public static final int ID_MOVE_PLAYER = 0x13;
    public static final int ID_PLAYER_ACTION = 0x24;
    public static final int ID_INTERACT = 0x21;
    public static final int ID_CONTAINER_OPEN = 0x2e;
    public static final int ID_CONTAINER_CLOSE = 0x2f;
    public static final int ID_INVENTORY_CONTENT = 0x31;
    public static final int ID_MOB_EQUIPMENT = 0x1f;
    public static final int ID_LEVEL_EVENT = 0x19;
    public static final int ID_UPDATE_BLOCK = 0x15;
    public static final int ID_UPDATE_ATTRIBUTES = 0x1d;
    public static final int ID_SET_ENTITY_DATA = 0x27;
    public static final int ID_RESPAWN = 0x2d;
    public static final int ID_MODAL_FORM_REQUEST = 0x64;
    public static final int ID_MODAL_FORM_RESPONSE = 0x65;
    public static final int ID_PLAYER_SKIN = 0x5d;
    public static final int ID_NETWORK_SETTINGS = 0x8f;
    public static final int ID_REQUEST_NETWORK_SETTINGS = 0xc1;

    public enum PlayStatus {
        LOGIN_SUCCESS(0),
        LOGIN_FAILED_CLIENT(1),
        LOGIN_FAILED_SERVER(2),
        PLAYER_SPAWN(3),
        LOGIN_FAILED_INVALID_TENANT(4),
        LOGIN_FAILED_VANILLA_EDU(5),
        LOGIN_FAILED_EDU_VANILLA(6),
        LOGIN_FAILED_SERVER_FULL(7);

        public final int code;

        PlayStatus(int code) {
            this.code = code;
        }
    }

    private BedrockPacketCodec() {}

    public record ContainerCloseDecode(int windowId, boolean serverInitiated) {
    }
    public enum StackActionType {
        TAKE, PLACE, SWAP, DROP, DESTROY, CONSUME, CREATE,
        CRAFT_RECIPE, CRAFT_RECIPE_AUTO, CRAFT_RECIPE_OPTIONAL, CRAFT_CREATIVE
    }

    public record StackAction(StackActionType type, int sourceSlot, int destSlot,
                              int count, int creativeNetworkId) {
    }

    public record ItemStackRequestDecode(int requestId, int actionCount,
                                         java.util.List<StackAction> actions) {
        public ItemStackRequestDecode(int requestId, int actionCount) {
            this(requestId, actionCount, java.util.List.of());
        }
    }



    public record MobEquipmentDecode(long runtimeId, int networkId, int inventorySlot, int hotbarSlot, int windowId) {}
    public record PlayerActionDecode(long entityRuntimeId, int action, int x, int y, int z, int face) {
        public boolean isBreakRelated() { return action == 0 || action == 1 || action == 2 || action == 18; }
        public boolean isPlaceRelated() { return action == 25 || action == 26; }
    }
    public record InventoryTxDecode(int transactionType, int requestId, int x, int y, int z, boolean hasPos) {
        public boolean likelyUseItemOn() { return transactionType == 2 || transactionType == 3; }
    }
    public record AuthInputDecode(float x, float y, float z, float pitch, float yaw, float headYaw, long tick) {}
    public record InteractDecode(byte action, long targetRuntimeId) {}
    public record Decoded(int id, ByteBuf body) {}
    public record MoveDecode(int runtimeId, float x, float y, float z, float pitch, float yaw) {}
    public record TextDecode(int type, boolean needsTranslation, String source, String message) {}
    public record CommandRequestDecode(String command) {}

    public static void writeUnsignedVarInt(ByteBuf out, int value) { BedrockCodecBinary.writeUnsignedVarInt(out, value); }
    public static int readUnsignedVarInt(ByteBuf in) { return BedrockCodecBinary.readUnsignedVarInt(in); }
    public static void writeString(ByteBuf out, String s) { BedrockCodecBinary.writeString(out, s); }
    public static String readString(ByteBuf in) { return BedrockCodecBinary.readString(in); }
    public static ByteBuf playStatus(PlayStatus status) { return BedrockLoginCodec.playStatus(status); }
    public static ByteBuf textChat(String source, String message) { return BedrockTextCodec.textChat(source, message); }
    public static ByteBuf movePlayer(long runtimeId, float x, float y, float z, float pitch, float yaw, float headYaw, byte mode, boolean onGround) { return BedrockWorldCodec.movePlayer(runtimeId, x, y, z, pitch, yaw, headYaw, mode, onGround); }
    public static ByteBuf startGame(long entityUniqueId, long runtimeId, String levelName, int blockX, int blockY, int blockZ, java.util.UUID worldId) { return BedrockLoginCodec.startGame(entityUniqueId, runtimeId, levelName, blockX, blockY, blockZ, worldId); }
    public static void writeEmptyNetworkNbt(ByteBuf out) { BedrockCodecBinary.writeEmptyNetworkNbt(out); }
    public static void writeZigZag64(ByteBuf out, long value) { BedrockCodecBinary.writeZigZag64(out, value); }
    public static void writeUnsignedVarLong(ByteBuf out, long value) { BedrockCodecBinary.writeUnsignedVarLong(out, value); }
    public static long readUnsignedVarLong(ByteBuf in) { return BedrockCodecBinary.readUnsignedVarLong(in); }
    public static ByteBuf resourcePacksInfoEmpty() { return BedrockLoginCodec.resourcePacksInfoEmpty(); }
    public static ByteBuf resourcePacksInfoOffer(java.util.UUID packId, String version, long sizeBytes,
                                                 String cdnUrl, boolean mustAccept) {
        return BedrockLoginCodec.resourcePacksInfoOffer(packId, version, sizeBytes, cdnUrl, mustAccept);
    }
    public static ByteBuf resourcePackStackEmpty() { return BedrockLoginCodec.resourcePackStackEmpty(); }
    public static ByteBuf resourcePackStackOffer(java.util.UUID packId, String version, boolean mustAccept) {
        return BedrockLoginCodec.resourcePackStackOffer(packId, version, mustAccept);
    }
    public static ByteBuf setTitle(int type, String text, int fadeIn, int stay, int fadeOut) {
        return BedrockUiCodec.setTitle(type, text, fadeIn, stay, fadeOut);
    }
    public static ByteBuf bossEventShow(long bossActorId, String title, float healthPercent, int color) {
        return BedrockUiCodec.bossEvent(bossActorId, BedrockUiCodec.BOSS_SHOW, title, healthPercent, color, 0);
    }
    public static ByteBuf bossEventHide(long bossActorId) {
        return BedrockUiCodec.bossEvent(bossActorId, BedrockUiCodec.BOSS_HIDE, "", 0f, 0, 0);
    }
    public static ByteBuf bossEventHealth(long bossActorId, float healthPercent) {
        return BedrockUiCodec.bossEvent(bossActorId, BedrockUiCodec.BOSS_HEALTH, "", healthPercent, 0, 0);
    }
    public static ByteBuf bossEventTitle(long bossActorId, String title) {
        return BedrockUiCodec.bossEvent(bossActorId, BedrockUiCodec.BOSS_TITLE, title, 0f, 0, 0);
    }
    public static ByteBuf setDisplayObjective(String slot, String objectiveId, String displayName,
                                               String criteria, int sortOrder) {
        return BedrockUiCodec.setDisplayObjective(slot, objectiveId, displayName, criteria, sortOrder);
    }
    public static ByteBuf setScore(int action, long scoreboardId, String objective, int score,
                                   int entryType, long actorUniqueId, String fakeName) {
        return BedrockUiCodec.setScore(action, scoreboardId, objective, score, entryType, actorUniqueId, fakeName);
    }
    public static ByteBuf blockActorSkull(int x, int y, int z, String ownerName) {
        return BedrockUiCodec.blockActorSkull(x, y, z, ownerName);
    }
    public static ByteBuf modalFormRequest(int formId, String json) { return BedrockLoginCodec.modalFormRequest(formId, json); }
    public static ByteBuf playerSkin(java.util.UUID uuid, String skinId, String skinDataBase64, String capeData, String geometry) { return BedrockLoginCodec.playerSkin(uuid, skinId, skinDataBase64, capeData, geometry); }
    public static ByteBuf containerOpen(int windowId, int windowType, int x, int y, int z, long entityRuntimeId) { return BedrockInventoryCodec.containerOpen(windowId, windowType, x, y, z, entityRuntimeId); }
    public static ByteBuf playerEnchantOptions(java.util.List<BedrockPaperRecipes.EnchantOption> options) { return BedrockInventoryCodec.playerEnchantOptions(options); }
    public static ByteBuf containerClose(int windowId, boolean serverInitiated) { return BedrockInventoryCodec.containerClose(windowId, serverInitiated); }
    public static ByteBuf containerSetData(int windowId, int property, int value) { return BedrockInventoryCodec.containerSetData(windowId, property, value); }
    public static ByteBuf updateTrade(int windowId, int windowType, int size, int tradeTier, boolean recipeAdded, boolean isEconomy, long traderEntityId, long playerEntityId, String displayName, java.util.List<int[]> offers) { return BedrockInventoryCodec.updateTrade(windowId, windowType, size, tradeTier, recipeAdded, isEconomy, traderEntityId, playerEntityId, displayName, offers); }
    public static ContainerCloseDecode tryDecodeContainerClose(ByteBuf body) { return BedrockInventoryCodec.tryDecodeContainerClose(body); }
    public static ByteBuf networkSettings(int compressionThreshold, int compressionAlgorithm, boolean clientThrottle, int clientThrottleThreshold, float clientThrottleScalar) { return BedrockLoginCodec.networkSettings(compressionThreshold, compressionAlgorithm, clientThrottle, clientThrottleThreshold, clientThrottleScalar); }
    public static ByteBuf networkSettingsUncompressed() { return BedrockLoginCodec.networkSettingsUncompressed(); }
    public static ByteBuf chunkRadiusUpdated(int radius) { return BedrockLoginCodec.chunkRadiusUpdated(radius); }
    public static ByteBuf networkChunkPublisherUpdate(int blockX, int blockY, int blockZ, int radiusBlocks) { return BedrockLoginCodec.networkChunkPublisherUpdate(blockX, blockY, blockZ, radiusBlocks); }
    public static ByteBuf levelChunkEmpty(int chunkX, int chunkZ) { return BedrockWorldCodec.levelChunkEmpty(chunkX, chunkZ); }
    public static ByteBuf levelChunkMarker(int chunkX, int chunkZ) { return BedrockWorldCodec.levelChunkMarker(chunkX, chunkZ); }
    public static int hashedAir() { return BedrockWorldCodec.hashedAir(); }
    public static int hashedDirt() { return BedrockWorldCodec.hashedDirt(); }
    public static int hashedStone() { return BedrockWorldCodec.hashedStone(); }
    public static int hashedGrass() { return BedrockWorldCodec.hashedGrass(); }
    public static int hashedBedrock() { return BedrockWorldCodec.hashedBedrock(); }
    public static ByteBuf levelChunkFlat(int chunkX, int chunkZ) { return BedrockWorldCodec.levelChunkFlat(chunkX, chunkZ); }
    public static ByteBuf levelChunkFromColumn(int chunkX, int chunkZ, int[][] states) { return BedrockWorldCodec.levelChunkFromColumn(chunkX, chunkZ, states); }
    public static ByteBuf addPlayer(java.util.UUID uuid, String username, long runtimeId, float x, float y, float z, float yaw, float pitch) { return BedrockEntityCodec.addPlayer(uuid, username, runtimeId, x, y, z, yaw, pitch); }
    public static ByteBuf setActorData(long runtimeId, String nametag, float health, float width, float height) { return BedrockEntityCodec.setActorData(runtimeId, nametag, health, width, height); }
    public static ByteBuf updateBlock(int x, int y, int z, int runtimeId, int flags, int layer) { return BedrockWorldCodec.updateBlock(x, y, z, runtimeId, flags, layer); }
    public static ByteBuf availableEntityIdentifiersEmpty() { return BedrockWorldCodec.availableEntityIdentifiersEmpty(); }
    public static ByteBuf biomeDefinitionListEmpty() { return BedrockWorldCodec.biomeDefinitionListEmpty(); }
    public static ByteBuf addActor(long uniqueId, long runtimeId, String actorType, float x, float y, float z, float yaw, float pitch) { return BedrockEntityCodec.addActor(uniqueId, runtimeId, actorType, x, y, z, yaw, pitch); }
    public static ByteBuf removeActor(long uniqueEntityId) { return BedrockEntityCodec.removeActor(uniqueEntityId); }
    public static ByteBuf setTime(int time) { return BedrockWorldCodec.setTime(time); }
    public static ByteBuf setDifficulty(int difficulty) { return BedrockWorldCodec.setDifficulty(difficulty); }
    public static ByteBuf setCommandsEnabled(boolean enabled) { return BedrockWorldCodec.setCommandsEnabled(enabled); }
    public static ByteBuf updateAttributesDefault(long runtimeId) { return BedrockLoginCodec.updateAttributesDefault(runtimeId); }
    public static ByteBuf creativeContentEmpty() { return BedrockInventoryCodec.creativeContentEmpty(); }
    public static ByteBuf creativeContentFull() { return BedrockInventoryCodec.creativeContentFull(); }
    public static ByteBuf availableCommandsEmpty() { return BedrockLoginCodec.availableCommandsEmpty(); }
    public static ByteBuf availableCommandsRich() { return BedrockLoginCodec.availableCommandsRich(); }
    public static ByteBuf playerListAddSelf(java.util.UUID uuid, long entityUniqueId, String username) { return BedrockLoginCodec.playerListAddSelf(uuid, entityUniqueId, username); }
    public static void writeMinimalSkin(ByteBuf out) { BedrockLoginCodec.writeMinimalSkin(out); }
    public static ByteBuf inventoryContentEmpty(int windowId, int size) { return BedrockInventoryCodec.inventoryContentEmpty(windowId, size); }
    public static ByteBuf inventoryContent(int windowId, int[] networkIds) { return BedrockInventoryCodec.inventoryContent(windowId, networkIds); }
    public static ByteBuf itemStackResponseOk(int requestId) { return BedrockInventoryCodec.itemStackResponseOk(requestId); }
    public static ItemStackRequestDecode tryDecodeItemStackRequest(ByteBuf body) { return BedrockInventoryCodec.tryDecodeItemStackRequest(body); }
    public static MobEquipmentDecode tryDecodeMobEquipment(ByteBuf body) { return BedrockInventoryCodec.tryDecodeMobEquipment(body); }
    public static void writeBlockPosition(ByteBuf out, int x, int y, int z) { BedrockCodecBinary.writeBlockPosition(out, x, y, z); }
    public static int[] readBlockPosition(ByteBuf in) { return BedrockCodecBinary.readBlockPosition(in); }
    public static void writeSignedVarInt(ByteBuf out, int value) { BedrockCodecBinary.writeSignedVarInt(out, value); }
    public static void writeSignedVarLong(ByteBuf out, long value) { BedrockCodecBinary.writeSignedVarLong(out, value); }
    public static void writeZigZag32(ByteBuf out, int value) { BedrockCodecBinary.writeZigZag32(out, value); }
    public static int readSignedVarInt(ByteBuf in) { return BedrockCodecBinary.readSignedVarInt(in); }
    public static PlayerActionDecode tryDecodePlayerAction(ByteBuf body) { return BedrockWorldCodec.tryDecodePlayerAction(body); }
    public static InventoryTxDecode tryDecodeInventoryTransaction(ByteBuf body) { return BedrockWorldCodec.tryDecodeInventoryTransaction(body); }
    public static AuthInputDecode tryDecodeAuthInput(ByteBuf body) { return BedrockWorldCodec.tryDecodeAuthInput(body); }
    public static InteractDecode tryDecodeInteract(ByteBuf body) { return BedrockWorldCodec.tryDecodeInteract(body); }
    public static MoveDecode tryDecodeMove(ByteBuf body) { return BedrockWorldCodec.tryDecodeMove(body); }
    public static TextDecode tryDecodeText(ByteBuf body) { return BedrockTextCodec.tryDecodeText(body); }
    public static CommandRequestDecode tryDecodeCommandRequest(ByteBuf body) { return BedrockTextCodec.tryDecodeCommandRequest(body); }
    public static ByteBuf commandOutputSimple(String message, boolean success) { return BedrockTextCodec.commandOutputSimple(message, success); }

    public static ByteBuf setActorData(long runtimeId, String actorType, String nametag, float health) {
        return BedrockEntityCodec.setActorData(runtimeId, actorType, nametag, health);
    }
    public static ByteBuf inventoryContent(int windowId, int[] networkIds, int[] counts) {
        return BedrockInventoryCodec.inventoryContent(windowId, networkIds, counts);
    }
    public static Decoded decode(ByteBuf packet) {
        int id = BedrockCodecBinary.readUnsignedVarInt(packet);
        return new Decoded(id, packet);
    }
    public static float[] aabbForActor(String actorType) {
        return BedrockActorAabb.forActor(actorType);
    }
}

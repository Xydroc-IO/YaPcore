package com.yapcore.crossplay.bedrock.cloudburst;

import com.yapcore.crossplay.bedrock.BedrockNbtDumps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;
import org.cloudburstmc.protocol.bedrock.data.BuildPlatform;
import org.cloudburstmc.protocol.bedrock.data.GameRuleData;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraAudioListener;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraPreset;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetCommandsEnabledPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetDifficultyPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetSpawnPositionPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTimePacket;
import org.cloudburstmc.protocol.bedrock.packet.SyncEntityPropertyPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAdventureSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;

/**
 * World / spawn / entity packet factories (split from {@link CloudburstPackets} for the ≤500-line gate).
 */
final class CloudburstPacketsWorld {

    private CloudburstPacketsWorld() {}

    /**
     * Geyser {@code ChunkUtils.sendEmptyChunk}: {@code subChunksLength=0} +
     * {@link #emptyBiomePayload()} (byte-exact vs Geyser EMPTY_CHUNK_PAYLOAD for 24 sections).
     */
    static LevelChunkPacket levelChunkEmpty(int chunkX, int chunkZ) {
        LevelChunkPacket packet = new LevelChunkPacket();
        packet.setChunkX(chunkX);
        packet.setChunkZ(chunkZ);
        packet.setDimension(0);
        packet.setSubChunksLength(0);
        packet.setRequestSubChunks(false);
        packet.setCachingEnabled(false);
        packet.setData(emptyBiomePayload());
        return packet;
    }

    /**
     * Cloudburst PlayerList ADD-self for proto ≥2168.
     * Hand-rolled {@code playerListAddSelf} with 0×0 skin images aborts 1.26.4x
     * (InitialConnection-90 / Block) during Phase-B — use a valid 64×64 RGBA skin.
     */
    static PlayerListPacket playerListAddSelf(UUID uuid, long entityUniqueId, String username) {
        String name = username == null || username.isBlank() ? "Player" : username;
        UUID id = uuid != null ? uuid : UUID.randomUUID();
        PlayerListPacket packet = new PlayerListPacket();
        packet.setAction(PlayerListPacket.Action.ADD);
        PlayerListPacket.Entry entry = new PlayerListPacket.Entry(id);
        entry.setAction(PlayerListPacket.Action.ADD);
        entry.setEntityId(entityUniqueId);
        entry.setName(name);
        entry.setXuid("");
        entry.setPlatformChatId("");
        entry.setBuildPlatform(BuildPlatform.UNKNOWN);
        entry.setSkin(minimalTrustedSkin(name));
        entry.setTeacher(false);
        entry.setHost(true);
        entry.setSubClient(false);
        entry.setTrustedSkin(true);
        entry.setColor(Color.WHITE);
        packet.getEntries().add(entry);
        return packet;
    }

    /** Valid wide custom skin — 64×64 RGBA (not ImageData.EMPTY / 0×0). */
    private static SerializedSkin minimalTrustedSkin(String fullSkinId) {
        byte[] rgba = new byte[64 * 64 * 4];
        // Flat opaque skin-tone fill so the client accepts the texture atlas.
        for (int i = 0; i < rgba.length; i += 4) {
            rgba[i] = (byte) 0xC6;
            rgba[i + 1] = (byte) 0x96;
            rgba[i + 2] = (byte) 0x7A;
            rgba[i + 3] = (byte) 0xFF;
        }
        String patch = "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}";
        String skinId = "Standard_Custom";
        String fullId = fullSkinId == null || fullSkinId.isBlank() ? skinId : fullSkinId;
        return SerializedSkin.builder()
                .skinId(skinId)
                .playFabId("")
                .skinResourcePatch(patch)
                .skinData(ImageData.of(64, 64, rgba))
                .animations(Collections.emptyList())
                .capeData(ImageData.EMPTY)
                .geometryData("")
                .geometryDataEngineVersion("1.14.0")
                .animationData("")
                .premium(false)
                .persona(false)
                .capeOnClassic(false)
                .primaryUser(true)
                .capeId("")
                .fullSkinId(fullId)
                .armSize("wide")
                .skinColor("#0")
                .color(new Color(0, true))
                .personaPieces(Collections.emptyList())
                .tintColors(Collections.emptyList())
                .overridingPlayerAppearance(false)
                .trusted(true)
                .profileHash("")
                .build();
    }

    /**
     * Geyser {@code syncEntityProperties()} — one packet per NBT in
     * {@code Registries.BEDROCK_ENTITY_PROPERTIES}. Vanilla Geyser with no extension
     * properties sends <em>zero</em> packets; empty NBT compounds can abort 1.26.x.
     * Optional classpath resource {@code protocol/bedrock/cloudburst/entity_properties.nbt}
     * (list of compounds, or a root with {@code properties} list) is loaded when present.
     */
    static List<SyncEntityPropertyPacket> syncEntityProperties() {
        List<NbtMap> maps = CloudburstEntityPropertyCache.load();
        if (maps.isEmpty()) {
            return List.of();
        }
        List<SyncEntityPropertyPacket> out = new ArrayList<>(maps.size());
        for (NbtMap data : maps) {
            SyncEntityPropertyPacket packet = new SyncEntityPropertyPacket();
            packet.setData(data != null ? data : NbtMap.EMPTY);
            out.add(packet);
        }
        return out;
    }

    /**
     * @deprecated Prefer {@link CloudburstPackets#syncEntityProperties()} — empty SyncEntityProperty is omitted.
     */
    @Deprecated
    static SyncEntityPropertyPacket syncEntityPropertyEmpty() {
        SyncEntityPropertyPacket packet = new SyncEntityPropertyPacket();
        packet.setData(NbtMap.EMPTY);
        return packet;
    }

    /**
     * Geyser {@code sendInitialGameRules()} — connect()-time only.
     * Do not merge Java Login {@code doimmediaterespawn} here; that arrives later.
     */
    static GameRulesChangedPacket gameRulesChangedInitial() {
        GameRulesChangedPacket packet = new GameRulesChangedPacket();
        packet.getGameRules().add(new GameRuleData<>("naturalregeneration", false));
        packet.getGameRules().add(new GameRuleData<>("keepinventory", true));
        packet.getGameRules().add(new GameRuleData<>("spawnradius", 0));
        packet.getGameRules().add(new GameRuleData<>("recipesunlock", true));
        packet.getGameRules().add(new GameRuleData<>("locatorBar", false));
        return packet;
    }

    /** Geyser {@code setClockRate(0)} → {@code sendGameRule("dodaylightcycle", false)}. */
    static GameRulesChangedPacket gameRuleDoDaylightCycle(boolean enabled) {
        GameRulesChangedPacket packet = new GameRulesChangedPacket();
        packet.getGameRules().add(new GameRuleData<>("dodaylightcycle", enabled));
        return packet;
    }

    /**
     * Geyser {@code JavaUpdateRecipesTranslator} stub — cleanRecipes=true, no recipe entries.
     */
    static CraftingDataPacket craftingDataClean() {
        CraftingDataPacket packet = new CraftingDataPacket();
        packet.setCleanRecipes(true);
        return packet;
    }

    /**
     * Geyser {@code JavaSetDefaultSpawnPositionTranslator} — WORLD_SPAWN at StartGame spawn.
     */
    static SetSpawnPositionPacket setSpawnPositionWorld(int blockX, int blockY, int blockZ) {
        SetSpawnPositionPacket packet = new SetSpawnPositionPacket();
        Vector3i pos = Vector3i.from(blockX, blockY, blockZ);
        packet.setSpawnType(SetSpawnPositionPacket.Type.WORLD_SPAWN);
        packet.setBlockPosition(pos);
        packet.setSpawnPosition(pos);
        packet.setDimensionId(0);
        packet.setSpawnForced(false);
        return packet;
    }

    /**
     * First-teleport stub matching Geyser after Java position: SERVER_READY Respawn
     * + MovePlayer RESPAWN to StartGame eye position.
     */
    static List<BedrockPacket> respawnReadyAndMove(
            long runtimeId, float x, float y, float z, float pitch, float yaw) {
        RespawnPacket respawn = new RespawnPacket();
        respawn.setRuntimeEntityId(runtimeId);
        respawn.setState(RespawnPacket.State.SERVER_READY);
        respawn.setPosition(Vector3f.from(x, y, z));
        MovePlayerPacket move = movePlayer(
                runtimeId, x, y, z, pitch, yaw, yaw, MovePlayerPacket.Mode.RESPAWN, true);
        return List.of(respawn, move);
    }

    /** Empty AvailableCommands — Geyser sends a real catalog later from Java; stub is empty OK. */
    static AvailableCommandsPacket availableCommandsEmpty() {
        return new AvailableCommandsPacket();
    }

    static SetTimePacket setTime(int time) {
        SetTimePacket packet = new SetTimePacket();
        packet.setTime(time);
        return packet;
    }

    static SetDifficultyPacket setDifficulty(int difficulty) {
        SetDifficultyPacket packet = new SetDifficultyPacket();
        packet.setDifficulty(difficulty);
        return packet;
    }

    /** Geyser always sends a full idlist — empty crashes some 26.x clients after spawn. */
    static AvailableEntityIdentifiersPacket availableEntityIdentifiers() {
        AvailableEntityIdentifiersPacket packet = new AvailableEntityIdentifiersPacket();
        NbtMap ids = BedrockNbtDumps.availableEntityIdentifiersNbt();
        packet.setIdentifiers(ids != null ? ids : NbtMap.EMPTY);
        return packet;
    }

    /** @deprecated Prefer {@link CloudburstPackets#availableEntityIdentifiers()}. */
    @Deprecated
    static AvailableEntityIdentifiersPacket availableEntityIdentifiersEmpty() {
        return availableEntityIdentifiers();
    }

    /**
     * Geyser {@code CameraDefinitions.CAMERA_PRESETS} — four vanilla + three geyser:* free variants.
     */
    static CameraPresetsPacket cameraPresetsVanilla() {
        CameraPresetsPacket packet = new CameraPresetsPacket();
        packet.getPresets().add(CameraPreset.builder().identifier("minecraft:first_person").build());
        packet.getPresets().add(CameraPreset.builder().identifier("minecraft:free").build());
        packet.getPresets().add(CameraPreset.builder().identifier("minecraft:third_person").build());
        packet.getPresets().add(CameraPreset.builder().identifier("minecraft:third_person_front").build());
        packet.getPresets().add(CameraPreset.builder()
                .identifier("geyser:free_audio")
                .parentPreset("minecraft:free")
                .listener(CameraAudioListener.PLAYER)
                .playEffect(OptionalBoolean.of(false))
                .build());
        packet.getPresets().add(CameraPreset.builder()
                .identifier("geyser:free_effects")
                .parentPreset("minecraft:free")
                .listener(CameraAudioListener.CAMERA)
                .playEffect(OptionalBoolean.of(true))
                .build());
        packet.getPresets().add(CameraPreset.builder()
                .identifier("geyser:free_audio_effects")
                .parentPreset("minecraft:free")
                .listener(CameraAudioListener.PLAYER)
                .playEffect(OptionalBoolean.of(true))
                .build());
        return packet;
    }

    /**
     * Geyser {@code ChunkUtils.squareToCircle}: {@code ceil((radius + 1) * √2)}.
     * Publisher block radius = squareToCircle(chunkRadius) ≪ 4.
     */
    static int squareToCircle(int chunkRadius) {
        return (int) Math.ceil((chunkRadius + 1) * Math.sqrt(2.0));
    }

    /**
     * Geyser {@code GeyserSession.sendAdventureSettings()} for survival/member.
     * abilitiesSet = {@code Ability.values()} (USED_ABILITIES); abilityValues for survival
     * are only BUILD/MINE/DOORS_AND_SWITCHES/OPEN_CONTAINERS (no attack/fly-speed flags).
     * Walk/fly speeds come from {@link com.yapcore.crossplay.bedrock.parity.MovementParityTable}.
     */
    static List<BedrockPacket> adventureSettingsSurvival(long uniqueEntityId) {
        return adventureSettingsSurvival(uniqueEntityId, 0.1f, 0.05f);
    }

    static List<BedrockPacket> adventureSettingsSurvival(
            long uniqueEntityId, float walkSpeed, float flySpeed) {
        UpdateAdventureSettingsPacket adventure = new UpdateAdventureSettingsPacket();
        adventure.setNoMvP(false);
        adventure.setNoPvM(false);
        adventure.setImmutableWorld(false);
        adventure.setShowNameTags(false);
        adventure.setAutoJump(true);

        UpdateAbilitiesPacket abilities = new UpdateAbilitiesPacket();
        abilities.setUniqueEntityId(uniqueEntityId);
        abilities.setCommandPermission(CommandPermission.ANY);
        abilities.setPlayerPermission(PlayerPermission.MEMBER);

        AbilityLayer base = new AbilityLayer();
        base.setLayerType(AbilityLayer.Type.BASE);
        base.setFlySpeed(flySpeed);
        base.setWalkSpeed(walkSpeed);
        base.setVerticalFlySpeed(1.0f);
        Collections.addAll(base.getAbilitiesSet(), Ability.values());
        Collections.addAll(base.getAbilityValues(),
                Ability.BUILD, Ability.MINE, Ability.DOORS_AND_SWITCHES, Ability.OPEN_CONTAINERS);
        abilities.getAbilityLayers().add(base);

        return List.of(adventure, abilities);
    }

    static SetPlayerGameTypePacket setPlayerGameTypeSurvival() {
        SetPlayerGameTypePacket packet = new SetPlayerGameTypePacket();
        packet.setGamemode(GameType.SURVIVAL.ordinal());
        return packet;
    }

    static SetCommandsEnabledPacket setCommandsEnabled(boolean enabled) {
        SetCommandsEnabledPacket packet = new SetCommandsEnabledPacket();
        packet.setCommandsEnabled(enabled);
        return packet;
    }

    /**
     * Geyser {@code recalculateBedrockRenderDistance}: Bedrock ChunkRadiusUpdated
     * radius is {@code squareToCircle(javaViewDistance)}, not the raw Java square view.
     */
    static ChunkRadiusUpdatedPacket chunkRadiusUpdatedFromJavaView(int javaViewDistance) {
        ChunkRadiusUpdatedPacket packet = new ChunkRadiusUpdatedPacket();
        packet.setRadius(squareToCircle(javaViewDistance));
        return packet;
    }

    /** Raw Bedrock circle radius (already converted). Prefer {@link CloudburstPackets#chunkRadiusUpdatedFromJavaView}. */
    static ChunkRadiusUpdatedPacket chunkRadiusUpdated(int bedrockCircleRadius) {
        ChunkRadiusUpdatedPacket packet = new ChunkRadiusUpdatedPacket();
        packet.setRadius(bedrockCircleRadius);
        return packet;
    }

    static NetworkChunkPublisherUpdatePacket networkChunkPublisherUpdate(
            int blockX, int blockY, int blockZ, int radiusBlocks) {
        NetworkChunkPublisherUpdatePacket packet = new NetworkChunkPublisherUpdatePacket();
        packet.setPosition(Vector3i.from(blockX, blockY, blockZ));
        packet.setRadius(radiusBlocks);
        return packet;
    }

    static TextPacket textChat(String source, String message) {
        TextPacket packet = new TextPacket();
        packet.setType(TextPacket.Type.CHAT);
        packet.setNeedsTranslation(false);
        packet.setSourceName(source == null ? "" : source);
        packet.setMessage(message == null ? "" : message);
        packet.setXuid("");
        packet.setPlatformChatId("");
        packet.setFilteredMessage("");
        return packet;
    }

    static UpdateBlockPacket updateBlock(int x, int y, int z, int runtimeId,
                                          CloudburstSession session) {
        UpdateBlockPacket packet = new UpdateBlockPacket();
        packet.setBlockPosition(Vector3i.from(x, y, z));
        packet.setDataLayer(0);
        packet.getFlags().addAll(UpdateBlockPacket.FLAG_ALL);
        BlockDefinition def = null;
        if (session != null) {
            def = session.palettes().blocks().getDefinition(runtimeId);
        }
        if (def == null) {
            def = new SimpleBlockDefinition("minecraft:air", runtimeId, NbtMap.EMPTY);
        }
        packet.setDefinition(def);
        return packet;
    }

    static MovePlayerPacket movePlayer(long runtimeId, float x, float y, float z,
                                        float pitch, float yaw, float headYaw,
                                        MovePlayerPacket.Mode mode, boolean onGround) {
        MovePlayerPacket packet = new MovePlayerPacket();
        packet.setRuntimeEntityId(runtimeId);
        packet.setPosition(Vector3f.from(x, y, z));
        packet.setRotation(Vector3f.from(pitch, yaw, headYaw));
        MovePlayerPacket.Mode resolved = mode != null ? mode : MovePlayerPacket.Mode.NORMAL;
        packet.setMode(resolved);
        // TELEPORT serialize requires non-null cause (Cloudburst v291+ ordinal write).
        if (resolved == MovePlayerPacket.Mode.TELEPORT) {
            packet.setTeleportationCause(MovePlayerPacket.TeleportationCause.UNKNOWN);
            packet.setEntityType(0);
        }
        packet.setOnGround(onGround);
        packet.setRidingRuntimeEntityId(0L);
        packet.setTick(0L);
        return packet;
    }

    static AddPlayerPacket addPlayer(UUID uuid, String username, long runtimeId,
                                     float x, float y, float z, float yaw, float pitch) {
        AddPlayerPacket packet = new AddPlayerPacket();
        packet.setUuid(uuid != null ? uuid : UUID.randomUUID());
        packet.setUsername(username == null ? "" : username);
        packet.setRuntimeEntityId(runtimeId);
        packet.setUniqueEntityId(runtimeId);
        packet.setPosition(Vector3f.from(x, y, z));
        packet.setRotation(Vector3f.from(pitch, yaw, yaw));
        packet.setMotion(Vector3f.ZERO);
        packet.setHand(ItemData.AIR);
        packet.setGameType(GameType.SURVIVAL);
        packet.setPlatformChatId("");
        packet.setDeviceId("");
        return packet;
    }

    /**
     * Geyser {@code ChunkUtils} EMPTY_CHUNK_PAYLOAD for overworld height 384 (24 sections).
     * Verified byte-exact against Geyser-Spigot {@code EMPTY_BIOME_DATA=[0x01,0x00]}
     * + 23×{@code 0xFF} biome section markers + border {@code 0x00}
     * → {@code 0100ffffffffffffffffffffffffffffffffffffffffffffff00} (26 bytes).
     */
    static ByteBuf emptyBiomePayload() {
        return emptyBiomePayload(24);
    }

    /** @param sections dimension height / 16 (overworld=24) */
    static ByteBuf emptyBiomePayload(int sections) {
        if (sections < 1) {
            throw new IllegalArgumentException("sections=" + sections);
        }
        ByteBuf payload = Unpooled.buffer(2 + sections);
        // EMPTY_BIOME_DATA: SingletonBitArray V0 runtime header + zigzag palette 0
        payload.writeByte(1);
        payload.writeByte(0);
        byte marker = (byte) 0xFF; // Geyser writes -1 for each remaining section slot
        for (int i = 0; i < sections - 1; i++) {
            payload.writeByte(marker);
        }
        payload.writeByte(0); // border block count
        return payload;
    }
}

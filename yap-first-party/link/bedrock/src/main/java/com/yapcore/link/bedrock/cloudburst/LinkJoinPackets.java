package com.yapcore.link.bedrock.cloudburst;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.BuildPlatform;
import org.cloudburstmc.protocol.bedrock.data.ChatRestrictionLevel;
import org.cloudburstmc.protocol.bedrock.data.ExperimentData;
import org.cloudburstmc.protocol.bedrock.data.GamePublishSetting;
import org.cloudburstmc.protocol.bedrock.data.GameRuleData;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission;
import org.cloudburstmc.protocol.bedrock.data.SpawnBiomeType;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraAudioListener;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraPreset;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParam;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Set;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemCategory;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket;
import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetCommandsEnabledPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTimePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAdventureSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.VoxelShapesPacket;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;

/** Join-path Cloudburst packet factories for Link (StartGame + registries subset). */
public final class LinkJoinPackets {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    private static volatile NbtMap actorIdentifiers;

    private LinkJoinPackets() {
    }

    public static VoxelShapesPacket voxelShapesEmpty() {
        VoxelShapesPacket packet = new VoxelShapesPacket();
        packet.setShapes(new ArrayList<>());
        packet.setNameMap(new HashMap<>());
        packet.setCustomShapeCount(0);
        return packet;
    }

    /**
     * Geyser-aligned StartGame positions: JE {@code feet} → Bedrock eye
     * {@code (feetX, feetY + PLAYER_EYE_OFFSET, feetZ)}; {@code defaultSpawn} = feet block.
     * When only block ints are known, pass {@code block + 0.5} on X/Z and block Y as feet Y.
     */
    public static StartGamePacket startGame(long uniqueId, long runtimeId, String levelName,
                                            double feetX, double feetY, double feetZ,
                                            LinkCloudburstCodecs.Session session) {
        int blockX = (int) Math.floor(feetX);
        int blockY = (int) Math.floor(feetY);
        int blockZ = (int) Math.floor(feetZ);
        StartGamePacket packet = new StartGamePacket();
        packet.setUniqueEntityId(uniqueId);
        packet.setRuntimeEntityId(runtimeId);
        packet.setPlayerGameType(GameType.SURVIVAL);
        // Bedrock playerPosition is eye height (Geyser SessionPlayerEntity#bedrockPosition).
        // Sending JE feet here made auth report Y≈feet as “eye”, so eye−1.62 never matched
        // lastSync and confirmOrHold stuck forever (freeze after SPAWNED).
        packet.setPlayerPosition(Vector3f.from(
                (float) feetX,
                (float) (feetY + LinkBedrockSession.PLAYER_EYE_OFFSET),
                (float) feetZ));
        packet.setRotation(Vector2f.from(1.0f, 1.0f));
        packet.setSeed(-1L);
        packet.setDimensionId(0);
        packet.setGeneratorId(1);
        packet.setLevelGameType(GameType.SURVIVAL);
        packet.setDifficulty(1);
        packet.setDefaultSpawn(Vector3i.from(blockX, blockY, blockZ));
        packet.setAchievementsDisabled(true);
        packet.setCurrentTick(-1L);
        packet.setEduEditionOffers(0);
        packet.setEduFeaturesEnabled(false);
        packet.setRainLevel(0.0f);
        packet.setLightningLevel(0.0f);
        packet.setMultiplayerGame(true);
        packet.setBroadcastingToLan(true);
        packet.setPlatformBroadcastMode(GamePublishSetting.PUBLIC);
        packet.setXblBroadcastMode(GamePublishSetting.PUBLIC);
        packet.setCommandsEnabled(true);
        packet.setTexturePacksRequired(false);
        packet.setBonusChestEnabled(false);
        packet.setStartingWithMap(false);
        packet.setTrustingPlayers(true);
        packet.setDefaultPlayerPermission(PlayerPermission.MEMBER);
        packet.setServerChunkTickRange(4);
        packet.setBehaviorPackLocked(false);
        packet.setResourcePackLocked(false);
        packet.setFromLockedWorldTemplate(false);
        packet.setUsingMsaGamertagsOnly(false);
        packet.setFromWorldTemplate(false);
        packet.setWorldTemplateOptionLocked(false);
        packet.setSpawnBiomeType(SpawnBiomeType.DEFAULT);
        packet.setCustomBiomeName("");
        packet.setEducationProductionId("");
        packet.setForceExperimentalGameplay(OptionalBoolean.empty());
        String name = levelName == null || levelName.isBlank() ? "YaP Link" : levelName;
        packet.setLevelId(name);
        packet.setLevelName(name);
        packet.setPremiumWorldTemplateId("00000000-0000-0000-0000-000000000000");
        packet.setEnchantmentSeed(0);
        packet.setMultiplayerCorrelationId("");
        if (session != null && session.palettes() != null && !session.palettes().blockProperties().isEmpty()) {
            packet.getBlockProperties().addAll(session.palettes().blockProperties());
        }
        packet.setVanillaVersion("*");
        // false: client may open own inventory without waiting for ContainerOpen (E key).
        // Geyser keeps true with full inventory translators; Link's path is incomplete.
        packet.setInventoriesServerAuthoritative(false);
        packet.setServerEngine("");
        packet.setPlayerPropertyData(NbtMap.EMPTY);
        packet.setWorldTemplateId(UUID.randomUUID());
        packet.setChatRestrictionLevel(ChatRestrictionLevel.NONE);
        packet.setRewindHistorySize(0);
        // true → digs arrive on PlayerAuthInput.playerActions (BedrockActionTranslator).
        // false would use classic PlayerActionPacket; keep true to match modern Geyser clients.
        packet.setServerAuthoritativeBlockBreaking(true);
        packet.setAuthoritativeMovementMode(AuthoritativeMovementMode.CLIENT);
        // REAL LevelChunk palette entries are NBT network_id (hashed). Must match mapper.
        // hashed=false + list-index runtimes made Bedrock treat solids as air → fall-through.
        packet.setBlockNetworkIdsHashed(true);
        packet.getExperiments().add(new ExperimentData("data_driven_items", true));
        packet.getExperiments().add(new ExperimentData("upcoming_creator_features", true));
        packet.getExperiments().add(new ExperimentData("experimental_molang_features", true));
        packet.setServerId("");
        packet.setWorldId("");
        packet.setScenarioId("");
        packet.setOwnerId("");
        return packet;
    }

    public static ItemComponentPacket itemComponentFull(LinkCloudburstCodecs.Session session) {
        ItemComponentPacket packet = new ItemComponentPacket();
        if (session != null) {
            packet.getItems().addAll(session.itemDefinitions());
        }
        return packet;
    }

    public static BiomeDefinitionListPacket biomeDefinitionListVanilla() {
        BiomeDefinitionListPacket packet = new BiomeDefinitionListPacket();
        packet.setDefinitions(NbtMap.EMPTY);
        packet.setBiomes(LinkPaletteRegistry.get().biomes());
        int count = packet.getBiomes() != null && packet.getBiomes().getDefinitions() != null
                ? packet.getBiomes().getDefinitions().size() : 0;
        if (count <= 0) {
            throw new IllegalStateException("BiomeDefinitionList vanilla empty — refusing encode");
        }
        LOG.info("BE BiomeDefinitionList vanilla count=" + count);
        return packet;
    }

    public static AvailableEntityIdentifiersPacket availableEntityIdentifiers() {
        AvailableEntityIdentifiersPacket packet = new AvailableEntityIdentifiersPacket();
        packet.setIdentifiers(loadActorIdentifiers());
        return packet;
    }

    public static CameraPresetsPacket cameraPresetsVanilla() {
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
     * PlayerList ADD-self with Geyser-valid Steve 64×64 + geometry (0×0 / empty-geo abort IC-90).
     */
    public static PlayerListPacket playerListAddSelf(UUID uuid, long entityUniqueId, String username) {
        return playerListAdd(uuid, entityUniqueId, username, true);
    }

    /** PlayerList ADD for a remote JE player (same trusted Steve skin). */
    public static PlayerListPacket playerListAddRemote(UUID uuid, long entityUniqueId, String username) {
        return playerListAdd(uuid, entityUniqueId, username, false);
    }

    private static PlayerListPacket playerListAdd(UUID uuid, long entityUniqueId, String username,
                                                  boolean host) {
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
        entry.setSkin(LinkTrustedSkin.steveWide());
        entry.setTeacher(false);
        entry.setHost(host);
        entry.setSubClient(false);
        entry.setTrustedSkin(true);
        entry.setColor(Color.WHITE);
        packet.getEntries().add(entry);
        return packet;
    }

    /** Survival interact unlock (inventory / dig / doors). Member command permission. */
    public static List<BedrockPacket> adventureSettingsSurvival(long uniqueEntityId) {
        return adventureSettingsSurvival(uniqueEntityId, 0);
    }

    /**
     * Survival adventure + abilities. JE op permission level ≥4 → Bedrock OPERATOR +
     * {@link Ability#OPERATOR_COMMANDS} (Geyser PermissionLevel mapping).
     */
    public static List<BedrockPacket> adventureSettingsSurvival(long uniqueEntityId,
                                                                int javaPermissionLevel) {
        UpdateAdventureSettingsPacket adventure = new UpdateAdventureSettingsPacket();
        adventure.setNoMvP(false);
        adventure.setNoPvM(false);
        adventure.setImmutableWorld(false);
        adventure.setShowNameTags(false);
        adventure.setAutoJump(true);

        boolean operator = javaPermissionLevel >= 4;
        UpdateAbilitiesPacket abilities = new UpdateAbilitiesPacket();
        abilities.setUniqueEntityId(uniqueEntityId);
        abilities.setCommandPermission(operator ? CommandPermission.OWNER : CommandPermission.ANY);
        abilities.setPlayerPermission(operator ? PlayerPermission.OPERATOR : PlayerPermission.MEMBER);

        AbilityLayer base = new AbilityLayer();
        base.setLayerType(AbilityLayer.Type.BASE);
        base.setFlySpeed(0.05f);
        base.setWalkSpeed(0.1f);
        base.setVerticalFlySpeed(1.0f);
        Collections.addAll(base.getAbilitiesSet(), Ability.values());
        List<Ability> values = new ArrayList<>();
        Collections.addAll(values,
                Ability.BUILD, Ability.MINE, Ability.DOORS_AND_SWITCHES, Ability.OPEN_CONTAINERS,
                Ability.ATTACK_PLAYERS, Ability.ATTACK_MOBS, Ability.TELEPORT);
        if (operator) {
            values.add(Ability.OPERATOR_COMMANDS);
            values.add(Ability.MAY_FLY);
        }
        base.getAbilityValues().addAll(values);
        abilities.getAbilityLayers().add(base);

        return List.of(adventure, abilities);
    }


    public static CreativeContentPacket creativeContentEmpty() {
        return LinkJoinPacketsCreative.creativeContentEmpty();
    }

    public static CreativeContentPacket creativeContentFull(LinkCloudburstCodecs.Session session) {
        return LinkJoinPacketsCreative.creativeContentFull(session);
    }

    public static AvailableCommandsPacket availableCommandsEmpty() {
        return LinkJoinPacketsCommands.availableCommandsEmpty();
    }

    public static AvailableCommandsPacket availableCommandsVanilla() {
        return LinkJoinPacketsCommands.availableCommandsVanilla();
    }

    public static AvailableCommandsPacket availableCommandsVanillaPlus(Iterable<String> extraNames) {
        return LinkJoinPacketsCommands.availableCommandsVanillaPlus(extraNames);
    }

    /** Minimal local-player metadata so the client has FLAGS / nametag / air. */
    public static SetEntityDataPacket setEntityDataLocalPlayer(long runtimeId, String username) {
        SetEntityDataPacket packet = new SetEntityDataPacket();
        packet.setRuntimeEntityId(runtimeId);
        packet.setTick(0L);
        EntityDataMap meta = new EntityDataMap();
        EnumMap<EntityFlag, Boolean> flags = new EnumMap<>(EntityFlag.class);
        flags.put(EntityFlag.CAN_SHOW_NAME, true);
        flags.put(EntityFlag.ALWAYS_SHOW_NAME, false);
        flags.put(EntityFlag.HAS_COLLISION, true);
        flags.put(EntityFlag.HAS_GRAVITY, true);
        flags.put(EntityFlag.BREATHING, true);
        meta.putFlags(flags);
        meta.put(EntityDataTypes.NAME, username != null ? username : "");
        meta.put(EntityDataTypes.AIR_SUPPLY, (short) 300);
        meta.put(EntityDataTypes.STRUCTURAL_INTEGRITY, 20);
        packet.setMetadata(meta);
        return packet;
    }

    public static PlayStatusPacket playerSpawn() {
        PlayStatusPacket packet = new PlayStatusPacket();
        packet.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
        return packet;
    }

    /** Ensure client is Survival (not spectator/adventure) after PLAYER_SPAWN. */
    public static SetPlayerGameTypePacket setPlayerGameTypeSurvival() {
        SetPlayerGameTypePacket packet = new SetPlayerGameTypePacket();
        packet.setGamemode(GameType.SURVIVAL.ordinal());
        return packet;
    }

    public static SetCommandsEnabledPacket setCommandsEnabled(boolean enabled) {
        SetCommandsEnabledPacket packet = new SetCommandsEnabledPacket();
        packet.setCommandsEnabled(enabled);
        return packet;
    }

    public static UpdateAttributesPacket updateAttributesMovementOnly(long runtimeId) {
        UpdateAttributesPacket packet = new UpdateAttributesPacket();
        packet.setRuntimeEntityId(runtimeId);
        packet.setTick(0L);
        packet.setAttributes(List.of(
                new AttributeData(
                        "minecraft:movement", 0f, Float.MAX_VALUE, 0.1f,
                        0f, Float.MAX_VALUE, 0.1f, List.of())));
        return packet;
    }

    public static GameRulesChangedPacket gameRulesChangedInitial() {
        GameRulesChangedPacket packet = new GameRulesChangedPacket();
        packet.getGameRules().add(new GameRuleData<>("naturalregeneration", false));
        packet.getGameRules().add(new GameRuleData<>("keepinventory", true));
        packet.getGameRules().add(new GameRuleData<>("spawnradius", 0));
        packet.getGameRules().add(new GameRuleData<>("recipesunlock", true));
        packet.getGameRules().add(new GameRuleData<>("locatorBar", false));
        return packet;
    }

    public static GameRulesChangedPacket gameRuleDoDaylightCycle(boolean enabled) {
        GameRulesChangedPacket packet = new GameRulesChangedPacket();
        packet.getGameRules().add(new GameRuleData<>("dodaylightcycle", enabled));
        return packet;
    }

    public static SetTimePacket setTime(int time) {
        SetTimePacket packet = new SetTimePacket();
        packet.setTime(time);
        return packet;
    }

    private static NbtMap loadActorIdentifiers() {
        NbtMap local = actorIdentifiers;
        if (local != null) {
            return local;
        }
        synchronized (LinkJoinPackets.class) {
            if (actorIdentifiers != null) {
                return actorIdentifiers;
            }
            String path = "protocol/bedrock/cloudburst/available_entity_identifiers.nbt";
            try (InputStream in = LinkJoinPackets.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    LOG.warning("Missing " + path + " — empty actor identifiers");
                    actorIdentifiers = NbtMap.EMPTY;
                    return actorIdentifiers;
                }
                byte[] bytes = in.readAllBytes();
                try (var nbt = NbtUtils.createNetworkReader(new ByteArrayInputStream(bytes))) {
                    Object root = nbt.readTag();
                    actorIdentifiers = root instanceof NbtMap map ? map : NbtMap.EMPTY;
                }
            } catch (Exception e) {
                LOG.warning("Actor identifiers load failed: " + e.getMessage());
                actorIdentifiers = NbtMap.EMPTY;
            }
            return actorIdentifiers;
        }
    }

}

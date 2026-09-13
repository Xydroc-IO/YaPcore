package com.yapcore.crossplay.bedrock.cloudburst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.BlockPropertyData;
import org.cloudburstmc.protocol.bedrock.data.ChatRestrictionLevel;
import org.cloudburstmc.protocol.bedrock.data.ExperimentData;
import org.cloudburstmc.protocol.bedrock.data.GamePublishSetting;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission;
import org.cloudburstmc.protocol.bedrock.data.SpawnBiomeType;
import org.cloudburstmc.protocol.bedrock.data.definitions.DimensionDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.VoxelShapesPacket;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;

/**
 * Join / login packet factories (split from {@link CloudburstPackets} for the ≤500-line gate).
 */
final class CloudburstPacketsLogin {

    private static final Logger LOG = Logger.getLogger("YaPcore.CloudburstPackets");

    private CloudburstPacketsLogin() {}

    /**
     * Geyser {@code startGame()} for 26.20+: empty {@link VoxelShapesPacket} before StartGame.
     * Missing this on 1.26.4x correlates with InitialConnection abort (codeword Block).
     */
    static VoxelShapesPacket voxelShapesEmpty() {
        VoxelShapesPacket packet = new VoxelShapesPacket();
        packet.setShapes(new ArrayList<>());
        packet.setNameMap(new HashMap<>());
        packet.setCustomShapeCount(0);
        return packet;
    }

    static PlayStatusPacket playStatusLoginSuccess() {
        PlayStatusPacket packet = new PlayStatusPacket();
        packet.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
        return packet;
    }

    /** Geyser {@code ServerToClientHandshakePacket} JWT (sent unencrypted before enableEncryption). */
    static ServerToClientHandshakePacket serverToClientHandshake(String jwt) {
        ServerToClientHandshakePacket packet = new ServerToClientHandshakePacket();
        packet.setJwt(jwt == null ? "" : jwt);
        return packet;
    }

    static PlayStatusPacket playerSpawn() {
        PlayStatusPacket packet = new PlayStatusPacket();
        packet.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
        return packet;
    }

    /** Geyser zlib + threshold 512. */
    static NetworkSettingsPacket networkSettingsGeyser() {
        NetworkSettingsPacket packet = new NetworkSettingsPacket();
        packet.setCompressionThreshold(512);
        packet.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);
        packet.setClientThrottleEnabled(false);
        packet.setClientThrottleThreshold(0);
        packet.setClientThrottleScalar(0f);
        return packet;
    }

    /**
     * Literal port of Geyser {@code buildStartGamePacket()} + {@code configureExperiments()}
     * + {@code startGame()} post-fields (serverId/worldId/scenarioId/ownerId empty).
     *
     * <p>Prefer {@link com.yapcore.crossplay.bedrock.geyserport.YapGeyserSession} StartGame
     * on the join path. This helper remains for encode tests / legacy callers.
     *
     * <p>Position/defaultSpawn use YaP spawn (Geyser uses placeholder 0,69,0 / ZERO until Java
     * Login teleports). Codec footnote: {@link AuthoritativeMovementMode#CLIENT} — Geyser v818+
     * omits mode on wire; YaP Cloudburst encode for proto 2169 still requires explicit CLIENT.
     * {@code blockNetworkIdsHashed=false} — Geyser default (empty LevelChunk join path).
     *
     * @param worldId ignored — Geyser uses {@link UUID#randomUUID()} for worldTemplateId
     */
    @SuppressWarnings("unused")
    static StartGamePacket startGame(long uniqueId, long runtimeId, String levelName,
                                     int blockX, int blockY, int blockZ, UUID worldId,
                                     CloudburstSession session) {
        StartGamePacket packet = new StartGamePacket();
        // --- GeyserSession.buildStartGamePacket() ---
        packet.setUniqueEntityId(uniqueId);
        packet.setRuntimeEntityId(runtimeId);
        packet.setPlayerGameType(GameType.SURVIVAL);
        // YaP: real spawn feet (no Java Login teleport). Geyser placeholder was (0, 69, 0).
        // Feet — not eye (+1.62); matches Bedrock entity position / Geyser Login teleports.
        packet.setPlayerPosition(Vector3f.from(blockX + 0.5f, (float) blockY, blockZ + 0.5f));
        packet.setRotation(Vector2f.from(1.0f, 1.0f));
        packet.setSeed(-1L);
        packet.setDimensionId(0);
        packet.setGeneratorId(1);
        packet.setLevelGameType(GameType.SURVIVAL);
        packet.setDifficulty(1);
        // Geyser: defaultSpawn=ZERO until Java; YaP empty-world needs spawn column match.
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
        String name = levelName == null || levelName.isBlank() ? "YaPcore" : levelName;
        packet.setLevelId(name);
        packet.setLevelName(name);
        packet.setPremiumWorldTemplateId("00000000-0000-0000-0000-000000000000");
        packet.setEnchantmentSeed(0);
        packet.setMultiplayerCorrelationId("");
        // blockProperties: custom only (Geyser blockMappings.getBlockProperties())
        packet.setVanillaVersion("*");
        packet.setInventoriesServerAuthoritative(true);
        packet.setServerEngine("");
        packet.setPlayerPropertyData(NbtMap.EMPTY);
        // Geyser: UUID.randomUUID() — do not reuse player/floodgate UUID as world template.
        packet.setWorldTemplateId(UUID.randomUUID());
        packet.setChatRestrictionLevel(ChatRestrictionLevel.NONE);
        packet.setRewindHistorySize(0);
        packet.setServerAuthoritativeBlockBreaking(true);
        // Codec footnote: Geyser v818+ omits mode on wire; YaP encode still needs CLIENT for 2169.
        packet.setAuthoritativeMovementMode(AuthoritativeMovementMode.CLIENT);
        // Geyser never sets blockNetworkIdsHashed (default false).
        // Join fill uses EMPTY_CHUNK; hashed=true with empties has crashed Bedrock clients here.
        packet.setBlockNetworkIdsHashed(false);
        // Packet defaults already: eduSharedUri EMPTY, editor NON_EDITOR, networkPermissions DEFAULT.
        // dayCycleStopTime default 0; experimentsPreviouslyToggled default false.

        // --- GeyserSession.configureExperiments() ---
        packet.getExperiments().add(new ExperimentData("data_driven_items", true));
        packet.getExperiments().add(new ExperimentData("upcoming_creator_features", true));
        packet.getExperiments().add(new ExperimentData("experimental_molang_features", true));

        // --- GeyserSession.startGame() post-fields ---
        packet.setServerId("");
        packet.setWorldId("");
        packet.setScenarioId("");
        packet.setOwnerId("");

        if (session != null) {
            List<BlockPropertyData> custom = session.palettes().blockProperties();
            if (!custom.isEmpty()) {
                packet.getBlockProperties().addAll(custom);
            }
            LOG.info("BE StartGame palette band=" + session.palettes().band()
                    + " helperBlocks=" + session.palettes().blockDefinitionList().size()
                    + " helperItems=" + session.palettes().itemDefinitionList().size()
                    + " blockProperties=" + packet.getBlockProperties().size()
                    + " experiments=" + packet.getExperiments().size()
                    + " hashed=" + packet.isBlockNetworkIdsHashed()
                    + " movement=" + packet.getAuthoritativeMovementMode()
                    + " serverEngineEmpty=" + packet.getServerEngine().isEmpty()
                    + " dayCycleStop=" + packet.getDayCycleStopTime()
                    + " experimentsPrev=" + packet.isExperimentsPreviouslyToggled());
        }
        return packet;
    }

    static ItemComponentPacket itemComponentFull(CloudburstSession session) {
        ItemComponentPacket packet = new ItemComponentPacket();
        if (session != null) {
            packet.getItems().addAll(session.itemDefinitions());
        }
        return packet;
    }

    /**
     * Geyser {@code GeyserSession.connect()} sends DimensionData <em>only</em> when overworld
     * height is extended beyond vanilla (−64..320). Vanilla-height joins omit this packet.
     * Always sending it on 1.26.4x phones can abort InitialConnection with codeword Block.
     */
    static DimensionDataPacket dimensionDataOverworld() {
        DimensionDataPacket packet = new DimensionDataPacket();
        // Matches Geyser defaults: generatorType=5, dimensionType=3, packId nil.
        packet.getDefinitions().add(new DimensionDefinition(
                "minecraft:overworld",
                320,
                -64,
                5,
                3,
                new UUID(0L, 0L),
                null));
        return packet;
    }

    /** True only when minY/maxY leave vanilla Bedrock overworld (−64..320). */
    static boolean needsDimensionData(int minY, int maxY) {
        return minY < -64 || maxY > 320;
    }

    /**
     * @deprecated Prefer {@link CloudburstPackets#biomeDefinitionListVanilla()} — empty biomes → IC-90 Block.
     * Kept as an alias so accidental Empty call sites still send full vanilla.
     */
    @Deprecated
    static BiomeDefinitionListPacket biomeDefinitionListEmpty() {
        return biomeDefinitionListVanilla();
    }

    /**
     * Geyser {@code Registries.BIOMES} full list. Empty BiomeDefinitionList correlates with
     * Bedrock client InitialConnection-90 / codeword Block (see GeyserMC/Geyser#6673 pattern).
     */
    static BiomeDefinitionListPacket biomeDefinitionListVanilla() {
        BiomeDefinitionListPacket packet = new BiomeDefinitionListPacket();
        packet.setDefinitions(NbtMap.EMPTY);
        packet.setBiomes(CloudburstPaletteRegistry.get().biomes());
        int count = packet.getBiomes() != null && packet.getBiomes().getDefinitions() != null
                ? packet.getBiomes().getDefinitions().size() : 0;
        if (count <= 0) {
            throw new IllegalStateException("BiomeDefinitionList vanilla empty — refusing encode");
        }
        LOG.info("BE BiomeDefinitionList vanilla count=" + count);
        return packet;
    }

    /**
     * Geyser empty {@code ResourcePacksInfoPacket} (no CDN / no pack entries).
     * Matches {@code UpstreamPacketHandler} after LOGIN_SUCCESS when pack list is empty:
     * nil world template id, empty version string, forcedToAccept=false.
     */
    static ResourcePacksInfoPacket resourcePacksInfoEmpty() {
        ResourcePacksInfoPacket packet = new ResourcePacksInfoPacket();
        packet.setForcedToAccept(false);
        packet.setHasAddonPacks(false);
        packet.setScriptingEnabled(false);
        packet.setVibrantVisualsForceDisabled(false);
        packet.setWorldTemplateId(new UUID(0L, 0L));
        packet.setWorldTemplateVersion("");
        return packet;
    }

    /**
     * Geyser empty {@code ResourcePackStackPacket} after HAVE_ALL_PACKS (Phase-1, no CDN).
     * Matches {@code UpstreamPacketHandler}: forcedToAccept=false, gameVersion="*", empty packs.
     */
    static ResourcePackStackPacket resourcePackStackEmpty() {
        ResourcePackStackPacket packet = new ResourcePackStackPacket();
        packet.setForcedToAccept(false);
        packet.setGameVersion("*");
        packet.setExperimentsPreviouslyToggled(false);
        packet.setHasEditorPacks(false);
        return packet;
    }
}

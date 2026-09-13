package com.yapcore.crossplay.bedrock.geyserport;

import com.yapcore.crossplay.bedrock.bridge.BedrockBridgeContext;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstCodecIndex;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstPackets;
import com.yapcore.crossplay.bedrock.cloudburst.CloudburstSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.BlockPropertyData;
import org.cloudburstmc.protocol.bedrock.data.ChatRestrictionLevel;
import org.cloudburstmc.protocol.bedrock.data.ExperimentData;
import org.cloudburstmc.protocol.bedrock.data.GamePublishSetting;
import org.cloudburstmc.protocol.bedrock.data.GameRuleData;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission;
import org.cloudburstmc.protocol.bedrock.data.SpawnBiomeType;
import org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetCommandsEnabledPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.SyncEntityPropertyPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.VoxelShapesPacket;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;

/**
 * Port of Geyser {@code GeyserSession} StartGame / registry / initial-state helpers
 * (split from {@link YapGeyserSession} for the ≤500-line domain gate).
 */
final class YapGeyserSessionStartGame {

    private YapGeyserSessionStartGame() {}

    /** Port of Geyser {@code startGame()}. */
    static void startGame(YapGeyserSession s) {
        CloudburstSession cb = s.cloudburst();
        if (cb != null) {
            cb.helper().setItemDefinitions(cb.palettes().items());
            cb.helper().setBlockDefinitions(cb.palettes().blocks());
            cb.helper().setCameraPresetDefinitions(CloudburstSession.geyserCameraPresetDefinitions());
        }
        if (s.protocolVersion() >= CloudburstCodecIndex.MIN_MODERN) {
            VoxelShapesPacket voxelShapesPacket = new VoxelShapesPacket();
            voxelShapesPacket.setNameMap(new HashMap<>());
            voxelShapesPacket.setShapes(new ArrayList<>());
            voxelShapesPacket.setCustomShapeCount(0);
            s.sendUpstreamPacket(voxelShapesPacket);
        }

        StartGamePacket startGamePacket = buildStartGamePacket(s);
        configureExperiments(startGamePacket);
        startGamePacket.setServerId("");
        startGamePacket.setWorldId("");
        startGamePacket.setScenarioId("");
        startGamePacket.setOwnerId("");
        BedrockBridgeContext.LOG.info(
                "BE StartGame palette band="
                        + (cb != null ? cb.palettes().band() : "none")
                        + " helperBlocks="
                        + (cb != null ? cb.palettes().blockDefinitionList().size() : 0)
                        + " helperItems="
                        + (cb != null ? cb.palettes().itemDefinitionList().size() : 0)
                        + " blockProperties=" + startGamePacket.getBlockProperties().size()
                        + " experiments=" + startGamePacket.getExperiments().size()
                        + " hashed=" + startGamePacket.isBlockNetworkIdsHashed()
                        + " movement=" + startGamePacket.getAuthoritativeMovementMode()
                        + " serverEngineEmpty="
                        + (startGamePacket.getServerEngine() == null
                                || startGamePacket.getServerEngine().isEmpty())
                        + " dayCycleStop=" + startGamePacket.getDayCycleStopTime()
                        + " experimentsPrev=" + startGamePacket.isExperimentsPreviouslyToggled());
        s.sendUpstreamPacket(startGamePacket);
    }

    /**
     * Port of Geyser {@code buildStartGamePacket()} field-by-field.
     *
     * <p>Codec footnote: Geyser v818+ omits {@code AuthoritativeMovementMode} on the wire;
     * YaP Cloudburst encode for proto 2169 still requires explicit {@link AuthoritativeMovementMode#CLIENT}.
     */
    static StartGamePacket buildStartGamePacket(YapGeyserSession s) {
        StartGamePacket p = new StartGamePacket();
        p.setUniqueEntityId(s.runtimeId());
        p.setRuntimeEntityId(s.runtimeId());
        p.setPlayerGameType(GameType.SURVIVAL);
        // YaP has no Java ClientboundLogin teleport pipeline — StartGame must use Paper spawn.
        // Geyser can use (0,69,0) placeholders because Java Login moves the entity.
        p.setPlayerPosition(Vector3f.from(s.spawnX() + 0.5f, (float) s.spawnY(), s.spawnZ() + 0.5f));
        p.setRotation(Vector2f.from(1.0f, 1.0f));
        p.setSeed(-1L);
        p.setDimensionId(s.bedrockDimensionId());
        p.setGeneratorId(1);
        p.setLevelGameType(GameType.SURVIVAL);
        p.setDifficulty(1);
        p.setDefaultSpawn(Vector3i.from(s.spawnX(), s.spawnY(), s.spawnZ()));
        p.setAchievementsDisabled(true);
        p.setCurrentTick(-1L);
        p.setEduEditionOffers(0);
        p.setEduFeaturesEnabled(false);
        p.setRainLevel(0.0f);
        p.setLightningLevel(0.0f);
        p.setMultiplayerGame(true);
        p.setBroadcastingToLan(true);
        p.setPlatformBroadcastMode(GamePublishSetting.PUBLIC);
        p.setXblBroadcastMode(GamePublishSetting.PUBLIC);
        p.setCommandsEnabled(true);
        p.setTexturePacksRequired(false);
        p.setBonusChestEnabled(false);
        p.setStartingWithMap(false);
        p.setTrustingPlayers(true);
        p.setDefaultPlayerPermission(PlayerPermission.MEMBER);
        p.setServerChunkTickRange(4);
        p.setBehaviorPackLocked(false);
        p.setResourcePackLocked(false);
        p.setFromLockedWorldTemplate(false);
        p.setUsingMsaGamertagsOnly(false);
        p.setFromWorldTemplate(false);
        p.setWorldTemplateOptionLocked(false);
        p.setSpawnBiomeType(SpawnBiomeType.DEFAULT);
        p.setCustomBiomeName("");
        p.setEducationProductionId("");
        p.setForceExperimentalGameplay(OptionalBoolean.empty());
        String serverName = "YaPcore";
        p.setLevelId(serverName);
        p.setLevelName(serverName);
        p.setPremiumWorldTemplateId("00000000-0000-0000-0000-000000000000");
        p.setEnchantmentSeed(0);
        p.setMultiplayerCorrelationId("");
        if (s.cloudburst() != null) {
            List<BlockPropertyData> custom = s.cloudburst().palettes().blockProperties();
            if (!custom.isEmpty()) {
                p.getBlockProperties().addAll(custom);
            }
        }
        p.setVanillaVersion("*");
        p.setInventoriesServerAuthoritative(true);
        p.setServerEngine("");
        p.setPlayerPropertyData(NbtMap.EMPTY);
        p.setWorldTemplateId(UUID.randomUUID());
        p.setChatRestrictionLevel(ChatRestrictionLevel.NONE);
        p.setRewindHistorySize(0);
        p.setServerAuthoritativeBlockBreaking(true);
        // Codec footnote: Geyser v818+ omits mode on wire; YaP Cloudburst encode for proto 2169
        // still needs explicit CLIENT.
        p.setAuthoritativeMovementMode(AuthoritativeMovementMode.CLIENT);
        // Geyser buildStartGamePacket never sets blockNetworkIdsHashed (default false).
        // hashed=true + EMPTY_CHUNK fill has crashed Bedrock clients in this tree.
        p.setBlockNetworkIdsHashed(false);
        return p;
    }

    static void configureExperiments(StartGamePacket startGamePacket) {
        startGamePacket.getExperiments().add(new ExperimentData("data_driven_items", true));
        startGamePacket.getExperiments().add(new ExperimentData("upcoming_creator_features", true));
        startGamePacket.getExperiments().add(new ExperimentData("experimental_molang_features", true));
    }

    static void syncEntityProperties(YapGeyserSession s) {
        for (SyncEntityPropertyPacket packet : CloudburstPackets.syncEntityProperties()) {
            s.sendUpstreamPacket(packet);
        }
    }

    static void sendRegistryDefinitions(YapGeyserSession s) {
        s.sendUpstreamPacket(CloudburstPackets.biomeDefinitionListVanilla());
        s.sendUpstreamPacket(CloudburstPackets.availableEntityIdentifiers());
        s.sendUpstreamPacket(CloudburstPackets.cameraPresetsVanilla());
        s.sendUpstreamPacket(CloudburstPackets.creativeContentFull(s.cloudburst()));
    }

    static void sendInitialPlayerState(YapGeyserSession s) {
        PlayStatusPacket playStatusPacket = new PlayStatusPacket();
        playStatusPacket.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
        s.sendUpstreamPacket(playStatusPacket);

        SetCommandsEnabledPacket setCommandsEnabledPacket = new SetCommandsEnabledPacket();
        setCommandsEnabledPacket.setCommandsEnabled(true);
        s.sendUpstreamPacket(setCommandsEnabledPacket);

        var table = com.yapcore.crossplay.bedrock.parity.MovementParityTable.loadDefault();
        for (var pkt : CloudburstPackets.adventureSettingsSurvival(
                s.runtimeId(), table.speedF(), table.flySpeedF())) {
            s.sendUpstreamPacket(pkt);
        }
        UpdateAttributesPacket attributesPacket =
                CloudburstPackets.updateAttributesMovementOnly(s.runtimeId(), table.speedF());
        s.sendUpstreamPacket(attributesPacket);
    }

    static void sendInitialGameRules(YapGeyserSession s) {
        GameRulesChangedPacket gamerulePacket = new GameRulesChangedPacket();
        gamerulePacket.getGameRules().add(new GameRuleData<>("naturalregeneration", false));
        gamerulePacket.getGameRules().add(new GameRuleData<>("keepinventory", true));
        gamerulePacket.getGameRules().add(new GameRuleData<>("spawnradius", 0));
        gamerulePacket.getGameRules().add(new GameRuleData<>("recipesunlock", true));
        gamerulePacket.getGameRules().add(new GameRuleData<>("locatorBar", false));
        s.sendUpstreamPacket(gamerulePacket);
    }
}

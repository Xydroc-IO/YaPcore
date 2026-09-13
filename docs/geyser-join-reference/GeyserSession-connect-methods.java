/* Extracted from GeyserSession CFR decompile — connect()/startGame path */
package org.geysermc.geyser.session;

public void connect() {
        int minY = BedrockDimension.OVERWORLD.minY();
        int maxY = BedrockDimension.OVERWORLD.maxY();
        for (JavaDimension javaDimension : this.registryCache.registry(JavaRegistries.DIMENSION_TYPE).values()) {
            if (javaDimension.bedrockId() != 0) continue;
            minY = Math.min(minY, javaDimension.minY());
            maxY = Math.max(maxY, javaDimension.minY() + javaDimension.height());
        }
        minY = Math.max(minY, -512);
        maxY = Math.min(maxY, 512);
        if (minY < BedrockDimension.OVERWORLD.minY() || maxY > BedrockDimension.OVERWORLD.maxY()) {
            boolean isInOverworld = this.bedrockDimension == this.bedrockOverworldDimension;
            this.bedrockOverworldDimension = new BedrockDimension(minY, maxY - minY, true, 0);
            if (isInOverworld) {
                this.bedrockDimension = this.bedrockOverworldDimension;
            }
            this.geyser.getLogger().debug("Extending overworld dimension to " + minY + " - " + maxY);
            DimensionDataPacket dimensionDataPacket = new DimensionDataPacket();
            dimensionDataPacket.getDefinitions().add(new DimensionDefinition("minecraft:overworld", maxY, minY, 5, 3, GeyserIntegratedPackUtil.INTEGRATED_PACK_UUID));
            this.upstream.sendPacket((BedrockPacket)dimensionDataPacket);
        }
        this.startGame();
        this.sentSpawnPacket = true;
        this.syncEntityProperties();
        ItemComponentPacket componentPacket = new ItemComponentPacket();
        componentPacket.getItems().addAll(this.itemMappings.getItemDefinitions().values());
        this.upstream.sendPacket((BedrockPacket)componentPacket);
        ChunkUtils.sendEmptyChunks(this, this.playerEntity.position().toInt(), 0, false);
        this.sendRegistryDefinitions();
        this.sendInitialPlayerState();
        this.sendInitialGameRules();
        this.resetTimeParameters();
        if (this.pendingSpectator) {
            this.pendingSpectator = false;
            SetPlayerGameTypePacket gameTypePacket = new SetPlayerGameTypePacket();
            gameTypePacket.setGamemode(GameType.SURVIVAL_VIEWER.ordinal());
            this.upstream.sendPacket((BedrockPacket)gameTypePacket);
        }
    }

private void startGame() {
        this.upstream.getCodecHelper().setItemDefinitions((DefinitionRegistry)this.itemMappings);
        this.upstream.getCodecHelper().setBlockDefinitions((DefinitionRegistry)this.blockMappings);
        this.upstream.getCodecHelper().setCameraPresetDefinitions(CameraDefinitions.CAMERA_DEFINITIONS);
        if (GameProtocol.is26_20orHigher((int)this.protocolVersion())) {
            VoxelShapesPacket voxelShapesPacket = new VoxelShapesPacket();
            voxelShapesPacket.setNameMap(new HashMap());
            voxelShapesPacket.setShapes(new ArrayList());
            this.upstream.sendPacket((BedrockPacket)voxelShapesPacket);
        }
        StartGamePacket startGamePacket = this.buildStartGamePacket();
        this.configureExperiments(startGamePacket);
        startGamePacket.setServerConfigurationJoinInfo(this.serverJoinInfo);
        if (this.playerEntity.getPropertyManager() != null) {
            startGamePacket.setPlayerPropertyData(this.playerEntity.getPropertyManager().toNbtMap("minecraft:player"));
        }
        startGamePacket.setServerId("");
        startGamePacket.setWorldId("");
        startGamePacket.setScenarioId("");
        startGamePacket.setOwnerId("");
        this.upstream.sendPacket((BedrockPacket)startGamePacket);
    }

private StartGamePacket buildStartGamePacket() {
        StartGamePacket startGamePacket = new StartGamePacket();
        startGamePacket.setUniqueEntityId(this.playerEntity.geyserId());
        startGamePacket.setRuntimeEntityId(this.playerEntity.geyserId());
        GameType gameType = EntityUtils.toBedrockGamemode((GameMode)this.gameMode);
        if (gameType == GameType.SURVIVAL_VIEWER && GameProtocol.is26_40orHigher((int)this.protocolVersion())) {
            gameType = GameType.SURVIVAL;
            this.pendingSpectator = true;
        }
        startGamePacket.setPlayerGameType(gameType);
        startGamePacket.setPlayerPosition(Vector3f.from((float)0.0f, (float)69.0f, (float)0.0f));
        startGamePacket.setRotation(Vector2f.from((float)1.0f, (float)1.0f));
        startGamePacket.setSeed(-1L);
        startGamePacket.setDimensionId(this.bedrockDimension.bedrockId());
        startGamePacket.setGeneratorId(1);
        startGamePacket.setLevelGameType(GameType.SURVIVAL);
        startGamePacket.setDifficulty(1);
        startGamePacket.setDefaultSpawn(Vector3i.ZERO);
        startGamePacket.setAchievementsDisabled(!this.geyser.config().gameplay().xboxAchievementsEnabled());
        startGamePacket.setCurrentTick(-1L);
        startGamePacket.setEduEditionOffers(0);
        startGamePacket.setEduFeaturesEnabled(false);
        startGamePacket.setRainLevel(0.0f);
        startGamePacket.setLightningLevel(0.0f);
        startGamePacket.setMultiplayerGame(true);
        startGamePacket.setBroadcastingToLan(true);
        startGamePacket.setPlatformBroadcastMode(GamePublishSetting.PUBLIC);
        startGamePacket.setXblBroadcastMode(GamePublishSetting.PUBLIC);
        startGamePacket.setCommandsEnabled(!this.geyser.config().gameplay().xboxAchievementsEnabled());
        startGamePacket.setTexturePacksRequired(false);
        startGamePacket.setBonusChestEnabled(false);
        startGamePacket.setStartingWithMap(false);
        startGamePacket.setTrustingPlayers(true);
        startGamePacket.setDefaultPlayerPermission(PlayerPermission.MEMBER);
        startGamePacket.setServerChunkTickRange(4);
        startGamePacket.setBehaviorPackLocked(false);
        startGamePacket.setResourcePackLocked(false);
        startGamePacket.setFromLockedWorldTemplate(false);
        startGamePacket.setUsingMsaGamertagsOnly(false);
        startGamePacket.setFromWorldTemplate(false);
        startGamePacket.setWorldTemplateOptionLocked(false);
        startGamePacket.setSpawnBiomeType(SpawnBiomeType.DEFAULT);
        startGamePacket.setCustomBiomeName("");
        startGamePacket.setEducationProductionId("");
        startGamePacket.setForceExperimentalGameplay(OptionalBoolean.empty());
        String serverName = this.geyser.config().gameplay().serverName();
        startGamePacket.setLevelId(serverName);
        startGamePacket.setLevelName((CharSequence)serverName);
        startGamePacket.setPremiumWorldTemplateId("00000000-0000-0000-0000-000000000000");
        startGamePacket.setEnchantmentSeed(0);
        startGamePacket.setMultiplayerCorrelationId("");
        startGamePacket.getBlockProperties().addAll(this.blockMappings.getBlockProperties());
        startGamePacket.setVanillaVersion("*");
        startGamePacket.setInventoriesServerAuthoritative(true);
        startGamePacket.setServerEngine("");
        startGamePacket.setPlayerPropertyData(NbtMap.EMPTY);
        startGamePacket.setWorldTemplateId(UUID.randomUUID());
        startGamePacket.setChatRestrictionLevel(ChatRestrictionLevel.NONE);
        startGamePacket.setRewindHistorySize(0);
        startGamePacket.setServerAuthoritativeBlockBreaking(true);
        return startGamePacket;
    }

private void configureExperiments(StartGamePacket startGamePacket) {
        startGamePacket.getExperiments().add(new ExperimentData("data_driven_items", true));
        startGamePacket.getExperiments().add(new ExperimentData("upcoming_creator_features", true));
        startGamePacket.getExperiments().add(new ExperimentData("experimental_molang_features", true));
    }

private void sendInitialPlayerState() {
        PlayStatusPacket playStatusPacket = new PlayStatusPacket();
        playStatusPacket.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
        this.upstream.sendPacket((BedrockPacket)playStatusPacket);
        SetCommandsEnabledPacket setCommandsEnabledPacket = new SetCommandsEnabledPacket();
        setCommandsEnabledPacket.setCommandsEnabled(!this.geyser.config().gameplay().xboxAchievementsEnabled());
        this.upstream.sendPacket((BedrockPacket)setCommandsEnabledPacket);
        UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
        attributesPacket.setRuntimeEntityId(this.getPlayerEntity().geyserId());
        attributesPacket.setAttributes(Collections.singletonList(GeyserAttributeType.MOVEMENT_SPEED.getAttribute()));
        this.upstream.sendPacket((BedrockPacket)attributesPacket);
    }

private void sendInitialGameRules() {
        GameRulesChangedPacket gamerulePacket = new GameRulesChangedPacket();
        gamerulePacket.getGameRules().add(new GameRuleData("naturalregeneration", (Object)false));
        gamerulePacket.getGameRules().add(new GameRuleData("keepinventory", (Object)true));
        gamerulePacket.getGameRules().add(new GameRuleData("spawnradius", (Object)0));
        gamerulePacket.getGameRules().add(new GameRuleData("recipesunlock", (Object)true));
        if (!GeyserWaypoint.uses26_10WaypointPacket((GeyserSession)this)) {
            gamerulePacket.getGameRules().add(new GameRuleData("locatorBar", (Object)false));
        } else {
            gamerulePacket.getGameRules().add(new GameRuleData("locatorBar", (Object)true));
        }
        this.upstream.sendPacket((BedrockPacket)gamerulePacket);
    }

public void resetTimeParameters() {
        this.setGameTicks(0L);
        this.setTimeTicks(0L, 0.0f);
        this.setClockRate(0.0f);
    }

public void setTimeTicks(long timeTicks, float partialTick) {
        this.dayTimeTicks = timeTicks;
        this.partialTimeTick = partialTick;
        this.synchronizeTime();
    }

public void setClockRate(float rate) {
        this.clockRate = rate;
        this.setShouldClientTickClock(this.clockRate == 1.0f);
    }

private void sendRegistryDefinitions() {
        BiomeDefinitionListPacket biomeDefinitionListPacket = new BiomeDefinitionListPacket();
        biomeDefinitionListPacket.setBiomes((BiomeDefinitions)Registries.BIOMES.get());
        this.upstream.sendPacket((BedrockPacket)biomeDefinitionListPacket);
        AvailableEntityIdentifiersPacket entityPacket = new AvailableEntityIdentifiersPacket();
        entityPacket.setIdentifiers((NbtMap)Registries.BEDROCK_ENTITY_IDENTIFIERS.get());
        this.upstream.sendPacket((BedrockPacket)entityPacket);
        CameraPresetsPacket cameraPresetsPacket = new CameraPresetsPacket();
        cameraPresetsPacket.getPresets().addAll(CameraDefinitions.CAMERA_PRESETS);
        this.upstream.sendPacket((BedrockPacket)cameraPresetsPacket);
        CreativeContentPacket creativePacket = new CreativeContentPacket();
        creativePacket.getContents().addAll(this.itemMappings.getCreativeItems());
        creativePacket.getGroups().addAll(this.itemMappings.getCreativeItemGroups());
        this.upstream.sendPacket((BedrockPacket)creativePacket);
    }

private void syncEntityProperties() {
        for (NbtMap nbtMap : (Set)Registries.BEDROCK_ENTITY_PROPERTIES.get()) {
            SyncEntityPropertyPacket syncEntityPropertyPacket = new SyncEntityPropertyPacket();
            syncEntityPropertyPacket.setData(nbtMap);
            this.upstream.sendPacket((BedrockPacket)syncEntityPropertyPacket);
        }

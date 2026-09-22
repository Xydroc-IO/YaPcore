package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.cloudburst.LinkJoinPackets;
import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession.JoinPhase;
import com.yapcore.link.bedrock.session.LinkBedrockSession.PendingChunkConsumer;
import com.yapcore.link.bedrock.translator.JavaDimensionTranslator;
import com.yapcore.link.bedrock.translator.JavaLoginTranslator;
import java.util.Locale;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTimePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;

/** Split from {@link LinkBedrockSession} for the ≤500-line domain gate. */
final class LinkBedrockSessionConnect {

    private final LinkBedrockSession s;
    private final LinkBedrockSessionConnectSpawn spawnLogic;

    LinkBedrockSessionConnect(LinkBedrockSession session) {
        this.s = session;
        this.spawnLogic = new LinkBedrockSessionConnectSpawn(session);
    }

    void connect() {
        if (!s.sentSpawnPacket) {
            if (s.codec != null) {
                s.codec.helper().setItemDefinitions(s.codec.palettes().items());
                s.codec.helper().setBlockDefinitions(s.codec.palettes().blocks());
                s.codec.helper().setCameraPresetDefinitions(LinkCloudburstCodecs.geyserCameraPresetDefinitions());
            }

            if (s.protocol >= 2168) {
                s.sendUpstreamPacket(LinkJoinPackets.voxelShapesEmpty());
            }

            double eyeY = s.spawnFeetY + 1.62;
            StartGamePacket startGame = LinkJoinPackets.startGame(
                    s.runtimeId, s.runtimeId, "YaP Link", s.spawnFeetX, s.spawnFeetY, s.spawnFeetZ, s.codec);
            LinkBedrockSession.LOG.info(
                    "BE StartGame palette band="
                            + (s.codec != null ? s.codec.palettes().band() : "none")
                            + " hashed="
                            + startGame.isBlockNetworkIdsHashed()
                            + " movement="
                            + startGame.getAuthoritativeMovementMode()
                            + " feet="
                            + fmt(s.spawnFeetX)
                            + ","
                            + fmt(s.spawnFeetY)
                            + ","
                            + fmt(s.spawnFeetZ)
                            + " eyeY="
                            + fmt(eyeY)
                            + " defaultSpawn="
                            + s.spawnX
                            + ","
                            + s.spawnY
                            + ","
                            + s.spawnZ
                            + " user="
                            + s.username);
            BedrockJoinProbe.noteEvent(
                    s.guid,
                    "StartGame feetY="
                            + fmt(s.spawnFeetY)
                            + " eyeY="
                            + fmt(eyeY)
                            + " defaultSpawn="
                            + s.spawnX
                            + ","
                            + s.spawnY
                            + ","
                            + s.spawnZ);
            s.sendUpstreamPacket(startGame);
            s.sentSpawnPacket = true;
            s.flushPendingAddEntities();
            s.sendUpstreamPacket(LinkJoinPackets.itemComponentFull(s.codec));
            s.sendUpstreamPacket(LinkJoinPackets.biomeDefinitionListVanilla());
            s.sendUpstreamPacket(LinkJoinPackets.availableEntityIdentifiers());
            s.sendUpstreamPacket(LinkJoinPackets.cameraPresetsVanilla());
            s.sendUpstreamPacket(LinkJoinPackets.creativeContentEmpty());
            s.sendUpstreamPacket(LinkJoinPackets.setCommandsEnabled(true));
            s.sendUpstreamPacket(LinkJoinPackets.updateAttributesMovementOnly(s.runtimeId));
            s.sendUpstreamPacket(LinkJoinPackets.gameRulesChangedInitial());
            SetTimePacket setTime = LinkJoinPackets.setTime(0);
            s.sendUpstreamPacket(setTime);
            s.sendUpstreamPacket(LinkJoinPackets.gameRuleDoDaylightCycle(false));
            s.setJoinPhase(JoinPhase.AWAITING_CLIENT_INIT);
            BedrockJoinProbe.noteEvent(s.guid, "connect complete awaiting_SOLID_then_PLAYER_SPAWN");
            LinkBedrockSession.LOG.info(
                    "BE LinkBedrockSession.connect complete user="
                            + s.username
                            + " spawnFeet="
                            + fmt(s.spawnFeetX)
                            + ","
                            + fmt(s.spawnFeetY)
                            + ","
                            + fmt(s.spawnFeetZ)
                            + " — waiting spawn-column SOLID before PLAYER_SPAWN / 0x71");
            spawnLogic.schedulePlayerSpawnTimeout();
        }
    }

    void tryCompletePlayerSpawn(String reason) {
        spawnLogic.tryCompletePlayerSpawn(reason);
    }

    void rearmPlayerSpawnAfterJoinSquare() {
        spawnLogic.rearmPlayerSpawnAfterJoinSquare();
    }

    void scheduleJoinInitAssist() {
        spawnLogic.scheduleJoinInitAssist();
    }

    boolean consumePostSpawnGroundHold(double feetX, double feetY, double feetZ, boolean onGround) {
        return spawnLogic.consumePostSpawnGroundHold(feetX, feetY, feetZ, onGround);
    }

    void resetPostSpawnGroundHold() {
        spawnLogic.resetPostSpawnGroundHold();
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    void setServerRenderDistance(int renderDistance) {
        renderDistance = Math.min(renderDistance, 96);
        s.serverRenderDistance = renderDistance;
        // Advertise the square we actually fill. squareToCircle(8)=13 made Bedrock
        // wait for a ring Folia never sends, so 0x71 arrived in minutes or not at all.
        int radius = Math.max(2, s.serverRenderDistance);
        ChunkRadiusUpdatedPacket chunkRadiusUpdatedPacket = new ChunkRadiusUpdatedPacket();
        chunkRadiusUpdatedPacket.setRadius(radius);
        s.sendUpstreamPacket(chunkRadiusUpdatedPacket);
    }

    void onJavaLoginPlay(JavaDownstreamClient.LoginPlayInfo info) {
        if (info == null) {
            return;
        }
        if (s.softBackendSwitch && s.sentSpawnPacket) {
            arriveSoftBackendSwitch(info);
            return;
        }
        s.pendingJavaView = info.viewDistance() > 0 ? info.viewDistance() : LinkBedrockSession.DEFAULT_JAVA_VIEW;
        s.setBedrockDimensionId(mapBedrockDimension(info.dimensionName()));
        // Cap Folia streaming during join. Advertising full view (32) made Bedrock wait
        // ~40–100s on the "Loading resource packs" screen while ~3500 LevelChunks arrived.
        // Full view is applied after SetLocalPlayerAsInitialized (0x71).
        JavaDownstreamClient down = s.downstream;
        if (down != null) {
            int joinView = Math.min(
                    JavaLoginTranslator.JOIN_BEDROCK_VIEW,
                    s.pendingJavaView);
            down.sendClientInformationView(joinView);
        }
        BedrockJoinProbe.noteEvent(
                s.guid, "java_login_play_deferred spawn joinView="
                        + Math.min(JavaLoginTranslator.JOIN_BEDROCK_VIEW,
                                s.pendingJavaView)
                        + " fullView=" + s.pendingJavaView
                        + " dim=" + info.dimensionName());
        LinkBedrockSession.LOG.info(
                "BE awaiting Java spawn before StartGame user="
                        + s.username
                        + " joinView="
                        + Math.min(JavaLoginTranslator.JOIN_BEDROCK_VIEW,
                                s.pendingJavaView)
                        + " fullView="
                        + s.pendingJavaView
                        + " dim="
                        + info.dimensionName());
        if (!s.awaitingJavaSpawn) {
            beginBedrockJoinIfNeeded();
        }
    }

    /**
     * Soft Connect arrive: Bedrock already has StartGame — ChangeDimension + clear remotes,
     * then wait for Folia spawn/chunks like a dimension change.
     */
    private void arriveSoftBackendSwitch(JavaDownstreamClient.LoginPlayInfo info) {
        String target = s.softBackendSwitchTarget;
        s.softBackendSwitch = false;
        s.softBackendSwitchTarget = null;
        s.clearPendingTeleport();
        s.softSwitchMoveGraceUntilMs = System.currentTimeMillis() + 8_000L;
        s.pendingJavaView = info.viewDistance() > 0 ? info.viewDistance() : LinkBedrockSession.DEFAULT_JAVA_VIEW;
        int beDim = mapBedrockDimension(info.dimensionName());
        // Force a dimension reload even when both backends are overworld.
        int fromDim = s.bedrockDimensionId();
        int transit = fromDim == 0 ? 1 : 0;
        JavaDimensionTranslator.onRespawn(s,
                new JavaDownstreamClient.RespawnInfo(0, info.dimensionName(), (byte) 0),
                transit);
        JavaDimensionTranslator.onRespawn(s,
                new JavaDownstreamClient.RespawnInfo(0, info.dimensionName(), (byte) 0),
                beDim);
        s.removeAllRemoteEntities();
        s.setJoinPhase(LinkBedrockSession.JoinPhase.SPAWNED);
        JavaDownstreamClient down = s.downstream;
        if (down != null) {
            int view = Math.max(
                    JavaLoginTranslator.MIN_POST_INIT_VIEW,
                    Math.min(32, s.pendingJavaView));
            down.sendClientInformationView(view);
            s.setServerRenderDistance(view);
        }
        s.awaitingJavaSpawn = true;
        BedrockJoinProbe.noteEvent(s.guid,
                "soft_switch_arrive target=" + target
                        + " entity=" + info.entityId()
                        + " dim=" + info.dimensionName()
                        + " view=" + s.pendingJavaView);
        LinkBedrockSession.LOG.info("BE soft-switch arrive user=" + s.username
                + " target=" + target
                + " entity=" + info.entityId()
                + " dim=" + info.dimensionName());
        sendPlayerLoadedOnce();
    }

    /**
     * JE {@code respawn} (portal / death / dim switch): reset column + publisher tracking and
     * tell Bedrock the new dimension so LevelChunks and fog radius stay valid.
     */
    void onJavaRespawn(JavaDownstreamClient.RespawnInfo info) {
        if (info == null) {
            return;
        }
        JavaDimensionTranslator.onRespawn(s, info, mapBedrockDimension(info.dimensionName()));
    }

    void onJavaSpawnPosition(double x, double y, double z) {
        s.setSpawnFromFeet(x, y, z);
        s.awaitingJavaSpawn = false;
        BedrockJoinProbe.noteEvent(
                s.guid,
                "java_spawn_position feet x="
                        + fmt(x)
                        + " y="
                        + fmt(y)
                        + " z="
                        + fmt(z)
                        + " block="
                        + s.spawnX
                        + ","
                        + s.spawnY
                        + ","
                        + s.spawnZ
                        + " dimId="
                        + s.bedrockDimension);
        LinkBedrockSession.LOG.info(
                "BE Folia spawn user="
                        + s.username
                        + " feet="
                        + fmt(x)
                        + ","
                        + fmt(y)
                        + ","
                        + fmt(z)
                        + " dimId="
                        + s.bedrockDimension
                        + " — same Folia overworld as JE when backend=chassis lobby");
        beginBedrockJoinIfNeeded();
    }

    void beginBedrockJoinIfNeeded() {
        if (!s.sentSpawnPacket) {
            connect();
            JavaLoginTranslator.afterConnect(s, s.pendingJavaView);
            sendPlayerLoadedOnce();
            flushPendingJeChunks();
            tryCompletePlayerSpawn("post_connect_flush");
            spawnLogic.schedulePlayerSpawnTimeout();
        }
    }

    static int mapBedrockDimension(String dimensionName) {
        if (dimensionName == null) {
            return 0;
        }

        return switch (dimensionName) {
            case "minecraft:the_nether" -> 1;
            case "minecraft:the_end" -> 2;
            default -> 0;
        };
    }

    void bufferOrTranslateLevelChunk(int chunkX, int chunkZ, io.netty.buffer.ByteBuf payload) {
        s.chunksLogic.bufferOrTranslateLevelChunk(chunkX, chunkZ, payload);
    }

    void bufferPendingRealChunk(int chunkX, int chunkZ, io.netty.buffer.ByteBuf payload) {
        s.chunksLogic.bufferPendingRealChunk(chunkX, chunkZ, payload);
    }

    int drainPendingRealChunks(PendingChunkConsumer consumer) {
        return s.chunksLogic.drainPendingRealChunks(consumer);
    }

    void noteRealJeChunkSent() {
        s.chunksLogic.noteRealJeChunkSent();
    }

    int realJeChunksSent() {
        return s.chunksLogic.realJeChunksSent();
    }

    void closeFromBedrock(String reason) {
        s.chunksLogic.closeFromBedrock(reason);
    }

    void sendPlayerLoadedOnce() {
        s.chunksLogic.sendPlayerLoadedOnce();
    }

    void flushPendingJeChunks() {
        s.chunksLogic.flushPendingJeChunks();
    }

    static long columnKey(int chunkX, int chunkZ) {
        return LinkBedrockSessionChunks.columnKey(chunkX, chunkZ);
    }
}

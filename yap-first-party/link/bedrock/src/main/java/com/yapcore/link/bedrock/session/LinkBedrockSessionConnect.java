package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.cloudburst.LinkJoinPackets;
import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession.JoinPhase;
import com.yapcore.link.bedrock.session.LinkBedrockSession.PendingChunkConsumer;
import com.yapcore.link.bedrock.session.LinkBedrockSession.PendingJeChunk;
import com.yapcore.link.bedrock.translator.ChunkUtils;
import com.yapcore.link.bedrock.translator.JavaBlockUpdateTranslator;
import com.yapcore.link.bedrock.translator.JavaDimensionTranslator;
import com.yapcore.link.bedrock.translator.JavaLevelChunkTranslator;
import com.yapcore.link.bedrock.translator.JavaLoginTranslator;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.Mode;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.TeleportationCause;
import org.cloudburstmc.protocol.bedrock.packet.SetTimePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;

/** Split from {@link LinkBedrockSession} for the ≤500-line domain gate. */
final class LinkBedrockSessionConnect {

    private final LinkBedrockSession s;

    LinkBedrockSessionConnect(LinkBedrockSession session) {
        this.s = session;
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
            schedulePlayerSpawnTimeout();
        }
    }

    void tryCompletePlayerSpawn(String reason) {
        if (s.sentSpawnPacket && !s.playerSpawnSent.get()) {
            boolean timeout = "timeout".equals(reason) || reason != null && reason.startsWith("timeout");
            if (s.spawnColumnSolid.get() || timeout) {
                if (s.playerSpawnSent.compareAndSet(false, true)) {
                    String spawnReason = s.spawnColumnSolid.get() ? "solid" : "timeout";
                    if (timeout && !s.spawnColumnSolid.get()) {
                        placeTemporarySpawnPlatform();
                    }

                    s.sendUpstreamPacket(LinkJoinPackets.playerSpawn());
                    s.sendUpstreamPacket(LinkJoinPackets.setPlayerGameTypeSurvival());
                    float eyeY = (float) (s.spawnFeetY + 1.62);
                    MovePlayerPacket move = new MovePlayerPacket();
                    move.setRuntimeEntityId(s.runtimeId);
                    move.setPosition(Vector3f.from((float) s.spawnFeetX, eyeY, (float) s.spawnFeetZ));
                    move.setRotation(Vector3f.from(s.pitch, s.yaw, s.yaw));
                    move.setMode(Mode.TELEPORT);
                    move.setTeleportationCause(TeleportationCause.UNKNOWN);
                    move.setEntityType(0);
                    move.setOnGround(true);
                    move.setRidingRuntimeEntityId(0L);
                    move.setTick(0L);
                    s.sendUpstreamPacket(move);
                    ChunkUtils.updateChunkPosition(s, s.spawnBlockPos());
                    s.armPostInitPositionConfirm(s.spawnFeetX, s.spawnFeetY, s.spawnFeetZ);
                    s.groundHoldTicksRemaining = LinkBedrockSession.POST_SPAWN_GROUND_HOLD_TICKS;
                    scheduleSpawnFreezeTeleports();
                    BedrockJoinProbe.noteEvent(
                            s.guid,
                            "stand_on surfaceY="
                                    + fmt(s.spawnFeetY)
                                    + " feetY="
                                    + fmt(s.spawnFeetY)
                                    + " eyeY="
                                    + fmt(eyeY)
                                    + " platform="
                                    + s.spawnPlatformPlaced);
                    BedrockJoinProbe.noteEvent(
                            s.guid,
                            "PLAYER_SPAWN+MovePlayer eyeY="
                                    + fmt(eyeY)
                                    + " feetY="
                                    + fmt(s.spawnFeetY)
                                    + " spawnReal="
                                    + s.spawnColumnReal.get()
                                    + " solidBlocks="
                                    + s.spawnColumnSolidBlocks.get()
                                    + " reason="
                                    + spawnReason);
                    LinkBedrockSession.LOG.info(
                            "BE PLAYER_SPAWN user="
                                    + s.username
                                    + " eyeY="
                                    + fmt(eyeY)
                                    + " feetY="
                                    + fmt(s.spawnFeetY)
                                    + " spawnReal="
                                    + s.spawnColumnReal.get()
                                    + " solidBlocks="
                                    + s.spawnColumnSolidBlocks.get()
                                    + " reason="
                                    + spawnReason
                                    + " platform="
                                    + s.spawnPlatformPlaced);
                }
            }
        }
    }

    void placeTemporarySpawnPlatform() {
        int standFeetBlockY = (int) Math.floor(s.spawnFeetY);
        int platformY = standFeetBlockY - 1;
        int stoneRt = s.stoneRuntimeId();
        JavaBlockUpdateTranslator.sendUpdateBlock(s, s.spawnX, platformY, s.spawnZ, stoneRt);
        s.setSpawnFromFeet(s.spawnFeetX, standFeetBlockY + 0.0, s.spawnFeetZ);
        s.punchStandOnAirCells();
        s.spawnPlatformPlaced = true;
        BedrockJoinProbe.noteEvent(
                s.guid,
                "spawn_platform stone y="
                        + platformY
                        + " standFeetY="
                        + standFeetBlockY
                        + " stoneRt="
                        + stoneRt
                        + " size=1 (timeout without solid)");
        BedrockJoinProbe.noteEvent(
                s.guid,
                "stand_on surfaceY="
                        + fmt(s.spawnFeetY)
                        + " feetY="
                        + fmt(s.spawnFeetY)
                        + " eyeY="
                        + fmt(s.spawnFeetY + 1.62)
                        + " platform=true");
        LinkBedrockSession.LOG.warning(
                "BE spawn platform (timeout, solidBlocks="
                        + s.spawnColumnSolidBlocks.get()
                        + ") platformY="
                        + platformY
                        + " feetY="
                        + standFeetBlockY
                        + " stoneRt="
                        + stoneRt
                        + " size=1 user="
                        + s.username);
    }

    void scheduleSpawnFreezeTeleports() {
        if (s.spawnFreezeScheduled.compareAndSet(false, true)) {
            JavaDownstreamClient down = s.downstream;
            if (down != null && down.channel() != null) {
                int[] remaining = new int[] {40};
                AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
                future.set(down.channel().eventLoop().scheduleAtFixedRate(
                        () -> {
                            if (s.joinPhase == JoinPhase.SPAWNED || remaining[0]-- <= 0) {
                                ScheduledFuture<?> f = future.get();
                                if (f != null) {
                                    f.cancel(false);
                                }
                            } else if (s.playerSpawnSent.get()) {
                                float eyeY = (float) (s.spawnFeetY + 1.62);
                                MovePlayerPacket move = new MovePlayerPacket();
                                move.setRuntimeEntityId(s.runtimeId);
                                move.setPosition(Vector3f.from((float) s.spawnFeetX, eyeY, (float) s.spawnFeetZ));
                                move.setRotation(Vector3f.from(s.pitch, s.yaw, s.yaw));
                                move.setMode(Mode.TELEPORT);
                                move.setTeleportationCause(TeleportationCause.UNKNOWN);
                                move.setEntityType(0);
                                move.setOnGround(true);
                                move.setRidingRuntimeEntityId(0L);
                                move.setTick(0L);
                                s.sendUpstreamPacket(move);
                            }
                        },
                        50L,
                        50L,
                        TimeUnit.MILLISECONDS));
            }
        }
    }

    void schedulePlayerSpawnTimeout() {
        if (s.playerSpawnTimeoutScheduled.compareAndSet(false, true)) {
            JavaDownstreamClient down = s.downstream;
            if (down != null && down.channel() != null) {
                down.channel()
                        .eventLoop()
                        .schedule(
                                () -> {
                                    if (!s.playerSpawnSent.get()) {
                                        BedrockJoinProbe.noteEvent(
                                                s.guid,
                                                "PLAYER_SPAWN timeout after "
                                                        + LinkBedrockSession.PLAYER_SPAWN_REAL_TIMEOUT_MS
                                                        + "ms reals="
                                                        + s.realJeChunksSent.get()
                                                        + " spawnReal="
                                                        + s.spawnColumnReal.get()
                                                        + " solidBlocks="
                                                        + s.spawnColumnSolidBlocks.get());
                                        tryCompletePlayerSpawn("timeout");
                                    }
                                },
                                LinkBedrockSession.PLAYER_SPAWN_REAL_TIMEOUT_MS,
                                TimeUnit.MILLISECONDS);
            }
        }
    }

    boolean consumePostSpawnGroundHold(double feetX, double feetY, double feetZ, boolean onGround) {
        if (s.groundHoldTicksRemaining <= 0) {
            return true;
        }

        boolean near = Math.abs(feetX - s.spawnFeetX) < 0.35
                && Math.abs(feetZ - s.spawnFeetZ) < 0.35
                && Math.abs(feetY - s.spawnFeetY) < 0.75;
        if (!near || !onGround && !(Math.abs(feetY - s.spawnFeetY) < 0.2)) {
            s.groundHoldTicksRemaining = LinkBedrockSession.POST_SPAWN_GROUND_HOLD_TICKS;
            if (s.pendingTeleportId < 0) {
                s.armPostInitPositionConfirm(s.spawnFeetX, s.spawnFeetY, s.spawnFeetZ);
            }

            return false;
        } else {
            s.groundHoldTicksRemaining--;
            if (s.groundHoldTicksRemaining <= 0) {
                BedrockJoinProbe.noteEvent(
                        s.guid, "auth ground-hold released feetY=" + fmt(feetY) + " onGround=" + onGround);
                return true;
            } else {
                return false;
            }
        }
    }

    void resetPostSpawnGroundHold() {
        s.groundHoldTicksRemaining = LinkBedrockSession.POST_SPAWN_GROUND_HOLD_TICKS;
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    void setServerRenderDistance(int renderDistance) {
        renderDistance = Math.min(renderDistance, 96);
        s.serverRenderDistance = renderDistance;
        int circle = ChunkUtils.squareToCircle(s.serverRenderDistance);
        ChunkRadiusUpdatedPacket chunkRadiusUpdatedPacket = new ChunkRadiusUpdatedPacket();
        chunkRadiusUpdatedPacket.setRadius(circle);
        s.sendUpstreamPacket(chunkRadiusUpdatedPacket);
    }

    void onJavaLoginPlay(JavaDownstreamClient.LoginPlayInfo info) {
        if (info != null) {
            s.pendingJavaView = info.viewDistance() > 0 ? info.viewDistance() : LinkBedrockSession.DEFAULT_JAVA_VIEW;
            s.setBedrockDimensionId(mapBedrockDimension(info.dimensionName()));
            // Align Folia Client Information with the server's advertised view (login_play)
            // so chunk streaming matches ChunkRadiusUpdated, not the old hardcoded 8.
            JavaDownstreamClient down = s.downstream;
            if (down != null) {
                down.sendClientInformationView(s.pendingJavaView);
            }
            BedrockJoinProbe.noteEvent(
                    s.guid, "java_login_play_deferred spawn view=" + s.pendingJavaView + " dim=" + info.dimensionName());
            LinkBedrockSession.LOG.info(
                    "BE awaiting Java spawn before StartGame user="
                            + s.username
                            + " view="
                            + s.pendingJavaView
                            + " dim="
                            + info.dimensionName());
            if (!s.awaitingJavaSpawn) {
                beginBedrockJoinIfNeeded();
            }
        }
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
            schedulePlayerSpawnTimeout();
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


    void bufferOrTranslateLevelChunk(int chunkX, int chunkZ, ByteBuf payload) {
        s.chunksLogic.bufferOrTranslateLevelChunk(chunkX, chunkZ, payload);
    }

    void bufferPendingRealChunk(int chunkX, int chunkZ, ByteBuf payload) {
        s.chunksLogic.bufferPendingRealChunk(chunkX, chunkZ, payload);
    }

    int drainPendingRealChunks(LinkBedrockSession.PendingChunkConsumer consumer) {
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

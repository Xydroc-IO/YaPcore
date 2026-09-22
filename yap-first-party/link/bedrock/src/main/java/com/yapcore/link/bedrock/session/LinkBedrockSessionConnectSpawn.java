package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.cloudburst.LinkJoinPackets;
import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession.JoinPhase;
import com.yapcore.link.bedrock.translator.ChunkUtils;
import com.yapcore.link.bedrock.translator.JavaBlockUpdateTranslator;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.Mode;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.TeleportationCause;

/** PLAYER_SPAWN / timeout / ground-hold (split from {@link LinkBedrockSessionConnect}). */
final class LinkBedrockSessionConnectSpawn {

    private final LinkBedrockSession s;

    LinkBedrockSessionConnectSpawn(LinkBedrockSession session) {
        this.s = session;
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
                    sendPlayerSpawnPackets(spawnReason);
                }
            }
        }
    }

    /**
     * After the join square is fully on the wire, re-emit PLAYER_SPAWN + MovePlayer + publisher
     * so Bedrock can leave "Generating world" without waiting for a ~45s client timeout.
     */
    void rearmPlayerSpawnAfterJoinSquare() {
        if (!s.sentSpawnPacket || s.joinPhase != JoinPhase.AWAITING_CLIENT_INIT) {
            return;
        }
        s.playerSpawnSent.set(true);
        sendPlayerSpawnPackets("join_square");
    }

    private void sendPlayerSpawnPackets(String spawnReason) {
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
        // Do not arm pending-teleport HOLD at PLAYER_SPAWN — soft-SPAWN / 0x71 sync
        // already anchors the client; HOLD froze Folia position at spawn.
        s.clearPendingTeleport();
        s.groundHoldTicksRemaining = LinkBedrockSession.POST_SPAWN_GROUND_HOLD_TICKS;
        scheduleSpawnFreezeTeleports();
        BedrockJoinProbe.noteEvent(
                s.guid,
                "stand_on surfaceY="
                        + LinkBedrockSessionConnect.fmt(s.spawnFeetY)
                        + " feetY="
                        + LinkBedrockSessionConnect.fmt(s.spawnFeetY)
                        + " eyeY="
                        + LinkBedrockSessionConnect.fmt(eyeY)
                        + " platform="
                        + s.spawnPlatformPlaced);
        BedrockJoinProbe.noteEvent(
                s.guid,
                "PLAYER_SPAWN+MovePlayer eyeY="
                        + LinkBedrockSessionConnect.fmt(eyeY)
                        + " feetY="
                        + LinkBedrockSessionConnect.fmt(s.spawnFeetY)
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
                        + LinkBedrockSessionConnect.fmt(eyeY)
                        + " feetY="
                        + LinkBedrockSessionConnect.fmt(s.spawnFeetY)
                        + " spawnReal="
                        + s.spawnColumnReal.get()
                        + " solidBlocks="
                        + s.spawnColumnSolidBlocks.get()
                        + " reason="
                        + spawnReason
                        + " platform="
                        + s.spawnPlatformPlaced);
    }

    /**
     * If Bedrock still has not sent 0x71 shortly after the join square filled, soft-SPAWN:
     * flush entities/shops and open soft-playable view (client slider 8–12). Loading screen
     * may linger until real 0x71 (which expands to full JE view).
     */
    void scheduleJoinInitAssist() {
        if (!s.joinInitAssistScheduled.compareAndSet(false, true)) {
            return;
        }
        JavaDownstreamClient down = s.downstream;
        if (down == null || down.channel() == null) {
            s.joinInitAssistScheduled.set(false);
            return;
        }
        down.channel().eventLoop().schedule(() -> {
            if (s.joinPhase != JoinPhase.AWAITING_CLIENT_INIT) {
                return;
            }
            BedrockJoinProbe.noteEvent(s.guid, "join_init_assist SOFT_SPAWN (no 0x71 yet)");
            LinkBedrockSession.LOG.info("BE join_init_assist SOFT_SPAWN user=" + s.username);
            // Same path as real 0x71 — idempotent when the client catches up later.
            com.yapcore.link.bedrock.translator.BedrockSetLocalPlayerAsInitializedTranslator
                    .translate(s, false);
        }, 1500L, TimeUnit.MILLISECONDS);
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
                        + LinkBedrockSessionConnect.fmt(s.spawnFeetY)
                        + " feetY="
                        + LinkBedrockSessionConnect.fmt(s.spawnFeetY)
                        + " eyeY="
                        + LinkBedrockSessionConnect.fmt(s.spawnFeetY + 1.62)
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
                int[] remaining = new int[] {8};
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
        // Burn the budget. Resetting it when the client is not perfectly on the spawn
        // point (and re-arming a teleport) locked Bedrock movement forever: onGround
        // never arrived, XZ drifted, and the player stood still while mobs killed them.
        s.groundHoldTicksRemaining--;
        if (s.groundHoldTicksRemaining > 0) {
            return false;
        }
        BedrockJoinProbe.noteEvent(
                s.guid,
                "auth ground-hold released feet="
                        + LinkBedrockSessionConnect.fmt(feetX) + ","
                        + LinkBedrockSessionConnect.fmt(feetY) + ","
                        + LinkBedrockSessionConnect.fmt(feetZ)
                        + " onGround=" + onGround);
        return true;
    }

    void resetPostSpawnGroundHold() {
        s.groundHoldTicksRemaining = LinkBedrockSession.POST_SPAWN_GROUND_HOLD_TICKS;
    }
}

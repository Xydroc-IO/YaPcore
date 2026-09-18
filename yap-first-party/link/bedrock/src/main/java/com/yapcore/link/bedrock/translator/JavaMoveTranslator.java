package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;

/**
 * JE player_position / entity move for self → Bedrock {@link MovePlayerPacket}.
 *
 * <p>Geyser {@code JavaPlayerPositionTranslator}: AcceptTeleport once per id + echo pos/rot;
 * Bedrock MovePlayer uses <em>eye</em> Y ({@link LinkBedrockSession#PLAYER_EYE_OFFSET}).
 * Duplicate ids and same-coord rubberbands must not spam MovePlayer TELEPORT.
 */
public final class JavaMoveTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaMoveTranslator() {
    }

    /** JE Synchronize Player Position (teleport) for the local player. */
    public static void onPlayerPosition(LinkBedrockSession session,
                                        double x, double y, double z,
                                        float yaw, float pitch,
                                        int teleportId) {
        if (session == null) {
            return;
        }
        if (!session.isSentSpawnPacket()) {
            session.onJavaSpawnPosition(x, y, z);
        }
        if (!session.isSentSpawnPacket()) {
            return;
        }

        // AcceptTeleport + move echo already sent in JavaDownstreamClient (once per id).
        // Do NOT sendAcceptTeleport again — Folia kicks Invalid move on duplicate id.
        if (teleportId >= 0 && teleportId == session.lastAcceptedTeleportId()) {
            LOG.fine("BE JavaMoveTranslator ignore duplicate tpId=" + teleportId
                    + " user=" + session.username());
            return;
        }

        // Stand-on lifted above Folia's buried Y — join-only snap. Once SPAWNED with soft-confirm
        // cleared, accept JE feet normally (dig-time player_position must not MovePlayer TELEPORT
        // back to stand-on; probe REJECT_BURIED rubberband).
        if (session.shouldRejectBuriedJeFeet(y)) {
            boolean softPending = session.pendingTeleportId() >= 0;
            boolean joinSnap = !session.isPlayerSpawnSent()
                    || softPending
                    || session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED;
            if (joinSnap) {
                double sx = session.spawnFeetX();
                double sy = session.spawnFeetY();
                double sz = session.spawnFeetZ();
                session.setPosition(sx, sy, sz, yaw, pitch);
                session.markTeleportAccepted(teleportId, sx, sy, sz);
                session.armPostInitPositionConfirm(sx, sy, sz);
                JavaDownstreamClient down = session.downstream();
                if (down != null) {
                    down.sendMovePosRot(sx, sy, sz, yaw, pitch, true);
                }
                if (session.isPlayerSpawnSent()) {
                    float eyeY = (float) (sy + LinkBedrockSession.PLAYER_EYE_OFFSET);
                    MovePlayerPacket move = new MovePlayerPacket();
                    move.setRuntimeEntityId(session.runtimeId());
                    move.setPosition(Vector3f.from((float) sx, eyeY, (float) sz));
                    move.setRotation(Vector3f.from(pitch, yaw, yaw));
                    move.setMode(MovePlayerPacket.Mode.TELEPORT);
                    move.setTeleportationCause(MovePlayerPacket.TeleportationCause.UNKNOWN);
                    move.setEntityType(0);
                    move.setOnGround(true);
                    move.setRidingRuntimeEntityId(0L);
                    move.setTick(0L);
                    session.sendUpstreamPacket(move);
                }
                BedrockJoinProbe.noteEvent(session.guid(),
                        "java_player_position→be REJECT_BURIED tp=" + teleportId
                                + " jeFeetY=" + String.format(java.util.Locale.ROOT, "%.3f", y)
                                + " standFeetY=" + String.format(java.util.Locale.ROOT, "%.3f", sy)
                                + " movePlayer=true");
                LOG.info("BE JavaMoveTranslator reject buried JE feet user=" + session.username()
                        + " jeY=" + (int) y + " standY=" + (int) sy);
                session.sendPlayerLoadedOnce();
                return;
            }
            session.clearStandOnLift("spawned_accept_je");
            // Fall through: treat as normal player_position (Folia actually sent this sync).
        }

        session.setPosition(x, y, z, yaw, pitch);
        boolean echoMovePlayer = session.markTeleportAccepted(teleportId, x, y, z);

        // Defer MovePlayer until PLAYER_SPAWN (after spawn-column REAL) so the client does not
        // simulate falling through the empty seed disk into terrain.
        if (echoMovePlayer && session.isPlayerSpawnSent()) {
            MovePlayerPacket move = new MovePlayerPacket();
            move.setRuntimeEntityId(session.runtimeId());
            // Bedrock position is eye height — sending JE feet caused auth-input −1.62 rubberbands.
            double eyeY = y + LinkBedrockSession.PLAYER_EYE_OFFSET;
            move.setPosition(Vector3f.from((float) x, (float) eyeY, (float) z));
            move.setRotation(Vector3f.from(pitch, yaw, yaw));
            move.setMode(MovePlayerPacket.Mode.TELEPORT);
            // Cloudburst v291+ writes cause.ordinal() when mode=TELEPORT — null NPE kills JE Netty thread.
            move.setTeleportationCause(MovePlayerPacket.TeleportationCause.UNKNOWN);
            move.setEntityType(0);
            move.setOnGround(false);
            move.setRidingRuntimeEntityId(0L);
            move.setTick(0L);
            session.sendUpstreamPacket(move);
            ChunkUtils.updateChunkPosition(session,
                    org.cloudburstmc.math.vector.Vector3i.from((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_player_position→be tp=" + teleportId
                            + " feetY=" + String.format(java.util.Locale.ROOT, "%.3f", y)
                            + " eyeY=" + String.format(java.util.Locale.ROOT, "%.3f", eyeY));
            LOG.info("BE JavaMoveTranslator teleport user=" + session.username()
                    + " tpId=" + teleportId
                    + " feet=" + (int) x + "," + (int) y + "," + (int) z
                    + " eyeY=" + String.format(java.util.Locale.ROOT, "%.3f", eyeY));
        } else if (echoMovePlayer) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_player_position→be DEFER_MOVE tp=" + teleportId
                            + " feetY=" + String.format(java.util.Locale.ROOT, "%.3f", y)
                            + " awaiting_PLAYER_SPAWN");
            LOG.info("BE JavaMoveTranslator defer MovePlayer until PLAYER_SPAWN user="
                    + session.username() + " tpId=" + teleportId);
        } else {
            // Same coords within epsilon — AcceptTeleport already satisfied Folia; skip TELEPORT echo.
            session.clearPendingTeleport();
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_player_position→be SKIP_ECHO tp=" + teleportId
                            + " x=" + (int) x + " y=" + (int) y + " z=" + (int) z);
            if ((teleportId & 63) == 0) {
                LOG.info("BE JavaMoveTranslator SKIP_ECHO user=" + session.username()
                        + " tpId=" + teleportId
                        + " pos=" + (int) x + "," + (int) y + "," + (int) z);
            }
        }

        // Unlock Folia chunk stream as soon as spawn+teleport are confirmed — do not wait ~50s for 0x71.
        session.sendPlayerLoadedOnce();
    }

    /**
     * JE move/teleport entity. Local player → MovePlayer; tracked mobs/animals →
     * {@link org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket}.
     */
    public static void onEntityMove(LinkBedrockSession session, int entityId,
                                    double x, double y, double z,
                                    float yaw, float pitch, boolean teleport) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        if (entityId == session.javaEntityId()) {
            session.setPosition(x, y, z, yaw, pitch);
            MovePlayerPacket move = new MovePlayerPacket();
            move.setRuntimeEntityId(session.runtimeId());
            move.setPosition(Vector3f.from(
                    (float) x,
                    (float) (y + LinkBedrockSession.PLAYER_EYE_OFFSET),
                    (float) z));
            move.setRotation(Vector3f.from(pitch, yaw, yaw));
            move.setMode(teleport ? MovePlayerPacket.Mode.TELEPORT : MovePlayerPacket.Mode.NORMAL);
            if (teleport) {
                move.setTeleportationCause(MovePlayerPacket.TeleportationCause.UNKNOWN);
                move.setEntityType(0);
            }
            move.setOnGround(false);
            move.setRidingRuntimeEntityId(0L);
            move.setTick(0L);
            session.sendUpstreamPacket(move);
            return;
        }
        Long runtime = session.runtimeForJava(entityId);
        if (runtime == null) {
            // Stash feet only — do NOT track. Premature track makes AddPlayer/AddEntity
            // treat the actor as already spawned and skip the spawn packet (invisible remotes).
            session.setEntityPos(entityId, (float) x, (float) y, (float) z, yaw, pitch);
            return;
        }
        session.setEntityPos(entityId, (float) x, (float) y, (float) z, yaw, pitch);
        JavaEntityTranslator.sendMoveAbsolute(session, runtime, x, y, z, yaw, pitch, teleport);
    }

    /**
     * JE teleport_entity with Relative bitmask (X=1 Y=2 Z=4 Y_ROT=8 X_ROT=16).
     */
    public static void onEntityTeleport(LinkBedrockSession session, int entityId,
                                        double x, double y, double z,
                                        float yaw, float pitch, int relatives) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        float[] prev = session.entityPos(entityId);
        double ax = x;
        double ay = y;
        double az = z;
        float ayaw = yaw;
        float apitch = pitch;
        if (prev != null) {
            if ((relatives & 1) != 0) {
                ax = prev[0] + x;
            }
            if ((relatives & 2) != 0) {
                ay = prev[1] + y;
            }
            if ((relatives & 4) != 0) {
                az = prev[2] + z;
            }
            if ((relatives & 8) != 0) {
                ayaw = prev[3] + yaw;
            }
            if ((relatives & 16) != 0) {
                apitch = prev[4] + pitch;
            }
        }
        onEntityMove(session, entityId, ax, ay, az, ayaw, apitch, true);
    }

    /** Apply JE relative move_entity_* using last known absolute position. */
    public static void onEntityRelativeMove(LinkBedrockSession session, int entityId,
                                            double dx, double dy, double dz,
                                            float yaw, float pitch, boolean rotationOnly) {
        onEntityRelativeMove(session, entityId, dx, dy, dz, yaw, pitch, rotationOnly, false);
    }

    public static void onEntityRelativeMove(LinkBedrockSession session, int entityId,
                                            double dx, double dy, double dz,
                                            float yaw, float pitch, boolean rotationOnly,
                                            boolean onGround) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        if (entityId == session.javaEntityId()) {
            return; // local player uses player_position / auth-input path
        }
        Long runtime = session.runtimeForJava(entityId);
        float[] prev = session.entityPos(entityId);
        if (runtime == null || prev == null) {
            return;
        }
        float x = prev[0] + (float) dx;
        float y = prev[1] + (float) dy;
        float z = prev[2] + (float) dz;
        float outYaw = Float.isNaN(yaw) ? prev[3] : yaw;
        float outPitch = Float.isNaN(pitch) ? prev[4] : pitch;
        if (rotationOnly) {
            x = prev[0];
            y = prev[1];
            z = prev[2];
        }
        session.setEntityPos(entityId, x, y, z, outYaw, outPitch);
        boolean forceTp = session.bumpEntityMoveForceAbsolute(entityId);
        JavaEntityTranslator.sendMoveAbsolute(session, runtime, x, y, z, outYaw, outPitch,
                forceTp, onGround);
    }
}

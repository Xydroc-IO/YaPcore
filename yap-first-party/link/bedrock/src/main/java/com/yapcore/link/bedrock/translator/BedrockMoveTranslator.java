package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.Set;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

/**
 * Bedrock PlayerAuthInput / MovePlayer → JE move_player_pos_rot on the Java downstream.
 *
 * <p>Geyser {@code BedrockMovePlayer} essentials: hold until teleport confirm (eye→feet),
 * then forward; always emit {@code ClientTickEnd} after SPAWNED; resend TELEPORT if stuck.
 * Also emit {@code player_input} before positions (Geyser {@code InputCache}, proto 776+).
 */
public final class BedrockMoveTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private BedrockMoveTranslator() {
    }

    public static void translateAuthInput(LinkBedrockSession session, PlayerAuthInputPacket auth) {
        if (session == null || auth == null || auth.getPosition() == null) {
            return;
        }
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            return;
        }
        // Dig/place embedded in AuthInput when serverAuthoritativeBlockBreaking=true — always
        // process while SPAWNED (even during teleport HOLD) so break is not gated on move unlock.
        BedrockActionTranslator.translateAuthInputActions(session, auth);

        // Geyser: player_input before positions (1.21.2+). Required for Folia attack/reach state.
        sendPlayerInputFromAuth(session, auth);

        Vector3f pos = auth.getPosition();
        Vector3f rot = auth.getRotation();
        float yaw = rot != null ? rot.getY() : session.yaw();
        float pitch = rot != null ? rot.getX() : session.pitch();
        // Auth-input Y is eye height on Bedrock; JE wants feet (Geyser PLAYER_ENTITY_OFFSET).
        double feetY = pos.getY() - LinkBedrockSession.PLAYER_EYE_OFFSET;
        double feetX = pos.getX();
        double feetZ = pos.getZ();
        // Keep session pose current even while JE move is HOLD'd — attacks aim from this.
        session.setPosition(feetX, feetY, feetZ, yaw, pitch);

        boolean pending = session.pendingTeleportId() >= 0;
        if (!session.confirmOrHoldAuthMove(feetX, feetY, feetZ)) {
            if (pending && session.pendingTeleportId() < 0) {
                BedrockJoinProbe.noteEvent(session.guid(),
                        "auth CONFIRM tp cleared feetY=" + fmt(feetY));
            } else if (pending) {
                if (session.shouldResendTeleport()) {
                    int held = session.unconfirmedAuthMoves();
                    resendLastTeleport(session);
                    session.resetUnconfirmedAuthMoves();
                    BedrockJoinProbe.noteEvent(session.guid(),
                            "auth HOLD resend TELEPORT held=" + held);
                } else if ((session.unconfirmedAuthMoves() & 15) == 1) {
                    BedrockJoinProbe.noteEvent(session.guid(),
                            "auth HOLD pendingTp=" + session.pendingTeleportId()
                                    + " feet=" + fmt(feetX) + "," + fmt(feetY) + "," + fmt(feetZ)
                                    + " held=" + session.unconfirmedAuthMoves());
                }
            }
            // Geyser still advances the JE client tick while teleport is unconfirmed.
            sendClientTickEnd(session);
            return;
        }

        Set<PlayerAuthInputData> input = auth.getInputData();
        boolean horizontalCollision = input != null
                && input.contains(PlayerAuthInputData.HORIZONTAL_COLLISION);
        // Geyser: onGround ≈ vertical collision while descending (delta Y &lt; 0).
        boolean onGround;
        if (input != null && input.contains(PlayerAuthInputData.VERTICAL_COLLISION)) {
            Vector3f delta = auth.getDelta();
            onGround = delta == null || delta.getY() < 0;
        } else {
            onGround = false;
        }
        if (!session.consumePostSpawnGroundHold(feetX, feetY, feetZ, onGround)) {
            if ((session.unconfirmedAuthMoves() & 15) == 1 || session.unconfirmedAuthMoves() == 0) {
                BedrockJoinProbe.noteEvent(session.guid(),
                        "auth HOLD ground feetY=" + fmt(feetY) + " onGround=" + onGround);
                resendLastTeleport(session);
            }
            sendClientTickEnd(session);
            return;
        }
        applyMove(session, feetX, feetY, feetZ, yaw, pitch, onGround, horizontalCollision);
        sendClientTickEnd(session);
    }

    /**
     * Map Bedrock AuthInput WASD/jump/sneak/sprint → JE {@code player_input} flags byte.
     * Analogue sticks: treat non-zero axes as digital presses (Geyser gamepad path lite).
     */
    private static void sendPlayerInputFromAuth(LinkBedrockSession session, PlayerAuthInputPacket auth) {
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            return;
        }
        Set<PlayerAuthInputData> input = auth.getInputData();
        boolean forward = input != null && (input.contains(PlayerAuthInputData.UP)
                || input.contains(PlayerAuthInputData.UP_LEFT)
                || input.contains(PlayerAuthInputData.UP_RIGHT));
        boolean backward = input != null && (input.contains(PlayerAuthInputData.DOWN)
                || input.contains(PlayerAuthInputData.DOWN_LEFT)
                || input.contains(PlayerAuthInputData.DOWN_RIGHT));
        boolean left = input != null && (input.contains(PlayerAuthInputData.LEFT)
                || input.contains(PlayerAuthInputData.UP_LEFT)
                || input.contains(PlayerAuthInputData.DOWN_LEFT));
        boolean right = input != null && (input.contains(PlayerAuthInputData.RIGHT)
                || input.contains(PlayerAuthInputData.UP_RIGHT)
                || input.contains(PlayerAuthInputData.DOWN_RIGHT));
        if (auth.getAnalogMoveVector() != null) {
            float ax = auth.getAnalogMoveVector().getX();
            float ay = auth.getAnalogMoveVector().getY();
            if (ay > 0.1f) {
                forward = true;
            }
            if (ay < -0.1f) {
                backward = true;
            }
            if (ax > 0.1f) {
                left = true;
            }
            if (ax < -0.1f) {
                right = true;
            }
        }
        boolean jump = input != null && (input.contains(PlayerAuthInputData.JUMP_DOWN)
                || input.contains(PlayerAuthInputData.JUMP_CURRENT_RAW)
                || input.contains(PlayerAuthInputData.AUTO_JUMPING_IN_WATER));
        boolean shift = input != null && (input.contains(PlayerAuthInputData.SNEAK_DOWN)
                || input.contains(PlayerAuthInputData.START_SNEAKING)
                || input.contains(PlayerAuthInputData.SNEAKING)
                || input.contains(PlayerAuthInputData.DESCEND));
        boolean sprint = input != null && input.contains(PlayerAuthInputData.SPRINT_DOWN);
        int flags = 0;
        if (forward) {
            flags |= 1;
        }
        if (backward) {
            flags |= 2;
        }
        if (left) {
            flags |= 4;
        }
        if (right) {
            flags |= 8;
        }
        if (jump) {
            flags |= 16;
        }
        if (shift) {
            flags |= 32;
        }
        if (sprint) {
            flags |= 64;
        }
        if (session.notePlayerInputFlags(flags)) {
            down.sendPlayerInput(forward, backward, left, right, jump, shift, sprint);
        }
    }

    public static void translateMovePlayer(LinkBedrockSession session, MovePlayerPacket move) {
        if (session == null || move == null || move.getPosition() == null) {
            return;
        }
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            return;
        }
        Vector3f pos = move.getPosition();
        Vector3f rot = move.getRotation();
        float yaw = rot != null ? rot.getY() : session.yaw();
        float pitch = rot != null ? rot.getX() : session.pitch();
        // MovePlayer from client is also eye-relative on modern auth-input editions.
        double feetY = pos.getY() - LinkBedrockSession.PLAYER_EYE_OFFSET;
        if (!session.confirmOrHoldAuthMove(pos.getX(), feetY, pos.getZ())) {
            sendClientTickEnd(session);
            return;
        }
        applyMove(session, pos.getX(), feetY, pos.getZ(), yaw, pitch, move.isOnGround(), false);
        sendClientTickEnd(session);
    }

    private static void applyMove(LinkBedrockSession session,
                                  double x, double y, double z,
                                  float yaw, float pitch,
                                  boolean onGround,
                                  boolean horizontalCollision) {
        session.setPosition(x, y, z, yaw, pitch);
        ChunkUtils.updateChunkPosition(session,
                org.cloudburstmc.math.vector.Vector3i.from((int) x, (int) y, (int) z));
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            return;
        }
        down.sendMovePosRot(x, y, z, yaw, pitch, onGround, horizontalCollision);
        long tick = session.nextMoveTick();
        if ((tick & 31L) == 0L) {
            LOG.info("BE→JE move user=" + session.username()
                    + " x=" + (int) x + " y=" + (int) y + " z=" + (int) z
                    + " onGround=" + onGround);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "auth→JE movePosRot y=" + (int) y + " onGround=" + onGround);
        }
    }

    private static void sendClientTickEnd(LinkBedrockSession session) {
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            return;
        }
        down.sendClientTickEnd();
    }

    /** Geyser resend of unconfirmed teleport as Bedrock MovePlayer TELEPORT (eye Y). */
    private static void resendLastTeleport(LinkBedrockSession session) {
        if (Double.isNaN(session.lastSyncX())) {
            return;
        }
        MovePlayerPacket move = new MovePlayerPacket();
        move.setRuntimeEntityId(session.runtimeId());
        move.setPosition(Vector3f.from(
                (float) session.lastSyncX(),
                (float) (session.lastSyncY() + LinkBedrockSession.PLAYER_EYE_OFFSET),
                (float) session.lastSyncZ()));
        move.setRotation(Vector3f.from(session.pitch(), session.yaw(), session.yaw()));
        move.setMode(MovePlayerPacket.Mode.TELEPORT);
        move.setTeleportationCause(MovePlayerPacket.TeleportationCause.UNKNOWN);
        move.setEntityType(0);
        move.setOnGround(false);
        move.setRidingRuntimeEntityId(0L);
        move.setTick(0L);
        session.sendUpstreamPacket(move);
        LOG.info("BE auth HOLD resend TELEPORT user=" + session.username()
                + " tpId=" + session.pendingTeleportId());
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}

package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.downstream.JavaPlayWire;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Bedrock → JE attack (proto 26.2 {@code minecraft:attack}) + swing.
 *
 * <p>Order matches Geyser on protocol 776+: {@code player_input} → facing move →
 * {@code attack} → {@code swing} → {@code client_tick_end}. Without input + tick-end,
 * Folia may accept the attack packet but never apply damage.
 *
 * <p>AuthInput {@code MISSED_SWING} / Animate arm-swing often arrive in the same burst as
 * {@code ITEM_USE_ON_ENTITY} attack. Sending a JE swing (+ tick-end) before the attack
 * resets {@code AttackStrengthTicker} (MC-255058) and can end the tick early — entity hits
 * then deal no damage. Miss swings are deferred on the downstream event-loop and cancelled
 * when an entity attack is translated (Geyser Animate deferral / air-hit coalescing).
 */
public final class BedrockCombat {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    /** Guid → pending air-miss swing awaiting event-loop flush. */
    private static final ConcurrentHashMap<Long, Boolean> PENDING_MISS_SWING = new ConcurrentHashMap<>();

    private BedrockCombat() {
    }

    public static void translateAttack(LinkBedrockSession session, long bedrockRuntimeId) {
        if (session == null || bedrockRuntimeId <= 0L) {
            return;
        }
        int javaEntityId = session.javaEntityForRuntime(bedrockRuntimeId);
        if (javaEntityId <= 0) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "BE→JE attack DROP unknown runtime=" + bedrockRuntimeId);
            LOG.info("BE→JE attack dropped unknown runtime=" + bedrockRuntimeId
                    + " user=" + session.username());
            return;
        }
        translateAttackJava(session, javaEntityId);
    }

    public static void translateAttackJava(LinkBedrockSession session, int javaEntityId) {
        if (session == null || javaEntityId <= 0) {
            return;
        }
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            return;
        }
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            return;
        }
        // Entity hit owns this burst's swing — drop deferred air-miss.
        cancelMissSwing(session);
        session.noteAttackTarget(javaEntityId);

        // Force player_input so Folia has attack/reach state this tick (Geyser InputCache).
        down.sendPlayerInput(false, false, false, false, false, false, false);
        session.notePlayerInputFlags(0);

        aimAtEntity(session, down, javaEntityId);

        String hex = JavaPlayWire.attackWireHex(javaEntityId);
        down.sendInteractAttack(javaEntityId, false);
        down.sendSwingArm(0);
        down.sendClientTickEnd();

        BedrockJoinProbe.noteEvent(session.guid(),
                "BE→JE attack entity=" + javaEntityId + " hex=" + hex);
        LOG.info("BE→JE attack entity=" + javaEntityId + " hex=" + hex
                + " user=" + session.username());
    }

    /**
     * AuthInput {@code MISSED_SWING} or Animate arm-swing — defer so a same-burst entity
     * attack can cancel (see class javadoc).
     */
    public static void noteMissSwing(LinkBedrockSession session) {
        if (session == null || session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            return;
        }
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            return;
        }
        PENDING_MISS_SWING.put(session.guid(), Boolean.TRUE);
        if (down.channel() != null && down.channel().isActive()) {
            down.channel().eventLoop().execute(() -> flushMissSwing(session));
        } else {
            flushMissSwing(session);
        }
    }

    /** Drop a deferred air-miss (entity attack consumed the swing). */
    public static void cancelMissSwing(LinkBedrockSession session) {
        if (session != null) {
            PENDING_MISS_SWING.remove(session.guid());
        }
    }

    /**
     * Air miss only: JE swing, <em>no</em> {@code client_tick_end} — AuthInput already ends
     * the tick (Geyser {@code BedrockPlayerAuthInputTranslator} MISSED_SWING).
     */
    public static void translateMissSwing(LinkBedrockSession session) {
        noteMissSwing(session);
    }

    static void flushMissSwing(LinkBedrockSession session) {
        if (session == null) {
            return;
        }
        if (!Boolean.TRUE.equals(PENDING_MISS_SWING.remove(session.guid()))) {
            return;
        }
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            return;
        }
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            return;
        }
        down.sendSwingArm(0);
        BedrockJoinProbe.noteEvent(session.guid(), "BE→JE swing MISSED_SWING");
    }

    private static void aimAtEntity(LinkBedrockSession session, JavaDownstreamClient down, int javaEntityId) {
        float yaw = session.yaw();
        float pitch = session.pitch();
        float[] target = session.entityPos(javaEntityId);
        double dist = -1;
        if (target != null) {
            double dx = target[0] - session.posX();
            double dy = (target[1] + 0.9) - (session.posY() + LinkBedrockSession.PLAYER_EYE_OFFSET);
            double dz = target[2] - session.posZ();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (horiz >= 1.0e-4 || Math.abs(dy) >= 1.0e-4) {
                yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
                pitch = (float) (Math.toDegrees(-Math.atan2(dy, Math.max(horiz, 1.0e-4))));
                pitch = Math.max(-90f, Math.min(90f, pitch));
            }
        }
        session.setPosition(session.posX(), session.posY(), session.posZ(), yaw, pitch);
        // Always push feet+look before attack so Folia isWithinAttackRange / Grim see this tick's pose.
        down.sendMovePosRot(session.posX(), session.posY(), session.posZ(), yaw, pitch, true, false);
        if (dist >= 0) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "BE→JE aim entity=" + javaEntityId
                            + " dist=" + String.format(java.util.Locale.ROOT, "%.2f", dist)
                            + " yaw=" + (int) yaw + " pitch=" + (int) pitch);
        }
    }
}

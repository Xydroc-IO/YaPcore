package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.downstream.JavaPlayWire;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.List;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

/**
 * Bedrock break/place/attack → JE dig / use_item_on / interact.
 *
 * <p>With {@code serverAuthoritativeBlockBreaking=true} (StartGame), modern clients embed dig
 * in {@link PlayerAuthInputPacket#getPlayerActions()} rather than classic {@link PlayerActionPacket}.
 * Both paths share {@link #translateBlockAction}.
 */
public final class BedrockActionTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private BedrockActionTranslator() {
    }

    public static void translatePlayerAction(LinkBedrockSession session, PlayerActionPacket act) {
        if (session == null || act == null) {
            return;
        }
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            return;
        }
        translateBlockAction(session, act.getAction(), act.getBlockPosition(), act.getFace(), "PlayerAction");
    }

    /**
     * Geyser-style: when StartGame sets server-auth block breaking, digs arrive on AuthInput
     * ({@link PlayerAuthInputData#PERFORM_BLOCK_ACTIONS} + {@code playerActions} list).
     */
    public static void translateAuthInputActions(LinkBedrockSession session, PlayerAuthInputPacket auth) {
        if (session == null || auth == null) {
            return;
        }
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            return;
        }
        List<PlayerBlockActionData> actions = auth.getPlayerActions();
        boolean flagged = auth.getInputData() != null
                && auth.getInputData().contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);
        if (actions != null && !actions.isEmpty()) {
            PlayerActionType first = actions.get(0).getAction();
            BedrockJoinProbe.noteEvent(session.guid(),
                    "auth blockActions n=" + actions.size()
                            + " flagged=" + flagged
                            + " first=" + first);
            for (PlayerBlockActionData action : actions) {
                if (action == null) {
                    continue;
                }
                translateBlockAction(session, action.getAction(), action.getBlockPosition(),
                        action.getFace(), "AuthInput");
            }
        }
        ItemUseTransaction itemUse = auth.getItemUseTransaction();
        if (itemUse != null && auth.getInputData() != null
                && auth.getInputData().contains(PlayerAuthInputData.PERFORM_ITEM_INTERACTION)) {
            translateItemUseTransaction(session, itemUse);
        }
        if (auth.getInputData() != null
                && auth.getInputData().contains(PlayerAuthInputData.PERFORM_ITEM_STACK_REQUEST)
                && auth.getItemStackRequest() != null) {
            ItemStackRequestPacket wrap = new ItemStackRequestPacket();
            wrap.getRequests().add(auth.getItemStackRequest());
            BedrockItemStackRequests.translate(session, wrap);
        }
        if (auth.getInputData() != null
                && auth.getInputData().contains(PlayerAuthInputData.MISSED_SWING)) {
            // Defer: same-burst ITEM_USE_ON_ENTITY attack must cancel this air-miss.
            BedrockCombat.noteMissSwing(session);
        }
    }

    static void translateBlockAction(LinkBedrockSession session,
                                     PlayerActionType type,
                                     Vector3i pos,
                                     int face,
                                     String via) {
        if (type == null) {
            return;
        }
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            return;
        }
        int x = pos != null ? pos.getX() : 0;
        int y = pos != null ? pos.getY() : 0;
        int z = pos != null ? pos.getZ() : 0;
        int seq = session.nextBlockSequence();

        if (type == PlayerActionType.START_BREAK) {
            int startSeq = session.beginOrContinueDig(x, y, z);
            if (startSeq < 0) {
                // Duplicate START on same block — swing only (probe 043021 START/ABORT loop).
                down.sendSwingArm(0);
                BedrockDigEffects.continueBreak(session, x, y, z);
                BedrockJoinProbe.noteEvent(session.guid(),
                        "BE→JE dig START coalesce via=" + via + " @" + x + "," + y + "," + z);
                return;
            }
            down.sendPlayerAction(JavaPlayWire.ACTION_START_DIG, x, y, z, face, startSeq);
            BedrockDigEffects.startBreak(session, x, y, z);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "BE→JE dig START via=" + via + " @" + x + "," + y + "," + z + " seq=" + startSeq);
            LOG.info("BE→JE dig start via=" + via + " " + x + "," + y + "," + z
                    + " user=" + session.username());
        } else if (type == PlayerActionType.CONTINUE_BREAK
                || type == PlayerActionType.BLOCK_CONTINUE_DESTROY) {
            // Do NOT re-send START_DIG — resets Folia dig progress → START/ABORT loops.
            if (!session.isDigging(x, y, z)) {
                int startSeq = session.beginOrContinueDig(x, y, z);
                if (startSeq >= 0) {
                    down.sendPlayerAction(JavaPlayWire.ACTION_START_DIG, x, y, z, face, startSeq);
                    BedrockDigEffects.startBreak(session, x, y, z);
                }
            } else {
                BedrockDigEffects.continueBreak(session, x, y, z);
            }
            down.sendSwingArm(0);
        } else if (type == PlayerActionType.ABORT_BREAK) {
            down.sendPlayerAction(JavaPlayWire.ACTION_ABORT_DIG, x, y, z, face, seq);
            BedrockDigEffects.stopBreak(session, x, y, z, false);
            session.clearDig();
            BedrockJoinProbe.noteEvent(session.guid(),
                    "BE→JE dig ABORT via=" + via + " @" + x + "," + y + "," + z);
        } else if (type == PlayerActionType.STOP_BREAK
                || type == PlayerActionType.BLOCK_PREDICT_DESTROY) {
            down.sendPlayerAction(JavaPlayWire.ACTION_STOP_DIG, x, y, z, face, seq);
            down.sendSwingArm(0);
            // Crack/break FX only — do NOT predict UpdateBlock air. Optimistic air lets the
            // client fall into a hole before Folia confirms, then player_position + REJECT_BURIED
            // rubberbands (MovePlayer TELEPORT). JE block_update drives air via JavaBlockUpdateTranslator.
            BedrockDigEffects.stopBreak(session, x, y, z, true);
            session.clearDig();
            BedrockJoinProbe.noteEvent(session.guid(),
                    "BE→JE dig STOP via=" + via + " @" + x + "," + y + "," + z + " seq=" + seq);
            LOG.info("BE→JE dig stop via=" + via + " " + x + "," + y + "," + z
                    + " user=" + session.username());
        } else if (type == PlayerActionType.DIMENSION_CHANGE_SUCCESS
                || type == PlayerActionType.DIMENSION_CHANGE_REQUEST_OR_CREATIVE_DESTROY_BLOCK) {
            if (type == PlayerActionType.DIMENSION_CHANGE_REQUEST_OR_CREATIVE_DESTROY_BLOCK
                    && pos != null) {
                int startSeq = session.beginOrContinueDig(x, y, z);
                if (startSeq < 0) {
                    startSeq = session.nextBlockSequence();
                }
                down.sendPlayerAction(JavaPlayWire.ACTION_START_DIG, x, y, z, face, startSeq);
                int seq2 = session.nextBlockSequence();
                down.sendPlayerAction(JavaPlayWire.ACTION_STOP_DIG, x, y, z, face, seq2);
                down.sendSwingArm(0);
                BedrockDigEffects.stopBreak(session, x, y, z, true);
                session.clearDig();
                BedrockJoinProbe.noteEvent(session.guid(),
                        "BE→JE dig CREATIVE via=" + via + " @" + x + "," + y + "," + z);
            } else {
                LOG.fine("BE PlayerAction dim/creative type=" + type + " user=" + session.username());
            }
        } else {
            down.sendUseItemOn(x, y, z, face, 0.5f, 0.5f, 0.5f, false, 0, seq);
            BedrockDigEffects.placeSound(session, x, y, z);
            LOG.fine("BE→JE use_item_on via=" + via + " action=" + type + " @" + x + "," + y + "," + z);
        }
    }

    public static void translateInventoryTransaction(LinkBedrockSession session,
                                                     InventoryTransactionPacket tx) {
        if (session == null || tx == null) {
            return;
        }
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            return;
        }
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            return;
        }
        InventoryTransactionType type = tx.getTransactionType();
        if (type == InventoryTransactionType.ITEM_USE_ON_ENTITY) {
            long runtime = tx.getRuntimeEntityId();
            int actionType = tx.getActionType();
            BedrockJoinProbe.noteEvent(session.guid(),
                    "be_INV_TX ITEM_USE_ON_ENTITY actionType=" + actionType
                            + " runtime=" + runtime
                            + " lookTarget=" + session.lastAttackTarget());
            // actionType 1 = attack (Geyser); 0 = interact/use. Some builds send other
            // non-1 values for tap-to-trade — treat anything except attack as shop open.
            if (runtime == 0L && session.lastAttackTarget() > 0) {
                runtime = session.lastAttackTarget();
            }
            if (runtime != 0L && actionType == 1) {
                BedrockCombat.translateAttack(session, runtime);
            } else if (actionType == 1 && session.lastAttackTarget() > 0) {
                BedrockCombat.translateAttackJava(session, session.lastAttackTarget());
            } else if (runtime != 0L) {
                BedrockInventoryOpen.useEntity(session, runtime);
            }
            return;
        }
        if (type == InventoryTransactionType.ITEM_USE) {
            Vector3i pos = tx.getBlockPosition();
            if (pos == null) {
                return;
            }
            int seq = session.nextBlockSequence();
            int face = tx.getBlockFace();
            // actionType 2 = break block (when inventoriesServerAuthoritative=false clients
            // may still send destroy via InventoryTransaction).
            if (tx.getActionType() == 2) {
                int startSeq = session.beginOrContinueDig(pos.getX(), pos.getY(), pos.getZ());
                if (startSeq < 0) {
                    startSeq = session.nextBlockSequence();
                }
                down.sendPlayerAction(JavaPlayWire.ACTION_START_DIG,
                        pos.getX(), pos.getY(), pos.getZ(), face, startSeq);
                int seq2 = session.nextBlockSequence();
                down.sendPlayerAction(JavaPlayWire.ACTION_STOP_DIG,
                        pos.getX(), pos.getY(), pos.getZ(), face, seq2);
                down.sendSwingArm(0);
                BedrockDigEffects.stopBreak(session, pos.getX(), pos.getY(), pos.getZ(), true);
                session.clearDig();
                BedrockJoinProbe.noteEvent(session.guid(),
                        "BE→JE dig DESTROY via=InventoryTransaction @"
                                + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                return;
            }
            down.sendUseItemOn(pos.getX(), pos.getY(), pos.getZ(), face,
                    clickX(tx.getClickPosition()), clickY(tx.getClickPosition()), clickZ(tx.getClickPosition()),
                    false, 0, seq);
            BedrockDigEffects.placeSound(session, pos.getX(), pos.getY(), pos.getZ());
            LOG.fine("BE→JE place/use @" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + " user=" + session.username());
        }
    }

    private static void translateItemUseTransaction(LinkBedrockSession session, ItemUseTransaction itemUse) {
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            return;
        }
        Vector3i pos = itemUse.getBlockPosition();
        if (pos == null) {
            return;
        }
        int seq = session.nextBlockSequence();
        int face = itemUse.getBlockFace();
        org.cloudburstmc.math.vector.Vector3f click = itemUse.getClickPosition();
        down.sendUseItemOn(pos.getX(), pos.getY(), pos.getZ(), face,
                clickX(click), clickY(click), clickZ(click), false, 0, seq);
        BedrockDigEffects.placeSound(session, pos.getX(), pos.getY(), pos.getZ());
        BedrockJoinProbe.noteEvent(session.guid(),
                "BE→JE use_item_on via=AuthInputItemUse @" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + " face=" + face
                        + " hit=" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f",
                        clickX(click), clickY(click), clickZ(click)));
    }

    private static float clickX(org.cloudburstmc.math.vector.Vector3f click) {
        return click != null ? clampHit(click.getX()) : 0.5f;
    }

    private static float clickY(org.cloudburstmc.math.vector.Vector3f click) {
        return click != null ? clampHit(click.getY()) : 0.5f;
    }

    private static float clickZ(org.cloudburstmc.math.vector.Vector3f click) {
        return click != null ? clampHit(click.getZ()) : 0.5f;
    }

    private static float clampHit(float v) {
        if (Float.isNaN(v)) {
            return 0.5f;
        }
        return Math.max(0f, Math.min(1f, v));
    }
}

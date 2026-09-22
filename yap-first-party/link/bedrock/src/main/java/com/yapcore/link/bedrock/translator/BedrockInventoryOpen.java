package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import io.netty.buffer.ByteBuf;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

/**
 * Bedrock E / OPEN_INVENTORY → ContainerOpen + inventory snapshot; ContainerClose → re-open ready.
 *
 * <p>Geyser echoes {@link ContainerClosePacket} upstream so the client clears open state —
 * without that echo, a second E key fails.
 */
public final class BedrockInventoryOpen {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private BedrockInventoryOpen() {
    }

    public static void onInteract(LinkBedrockSession session, InteractPacket interact) {
        if (session == null || interact == null) {
            return;
        }
        InteractPacket.Action action = interact.getAction();
        long runtime = interact.getRuntimeEntityId();
        BedrockJoinProbe.noteEvent(session.guid(),
                "be_Interact action=" + action + " runtime=" + runtime);
        if (action == InteractPacket.Action.OPEN_INVENTORY) {
            openPlayerInventory(session);
            return;
        }
        if (action == InteractPacket.Action.MOUSEOVER) {
            if (runtime != 0L) {
                session.noteAttackTarget((int) runtime);
            }
            return;
        }
        if (action == InteractPacket.Action.DAMAGE
                || action == InteractPacket.Action.INTERACT
                || action == InteractPacket.Action.NPC_OPEN) {
            int entityId = runtime != 0L ? (int) runtime : 0;
            if (entityId > 0) {
                session.noteAttackTarget(entityId);
            }
            if (action == InteractPacket.Action.DAMAGE && entityId > 0) {
                BedrockCombat.translateAttack(session, entityId);
            } else if (entityId > 0) {
                useEntity(session, runtime);
            } else if (session.lastAttackTarget() > 0
                    && (action == InteractPacket.Action.INTERACT
                    || action == InteractPacket.Action.NPC_OPEN)) {
                // Some builds send INTERACT with runtime 0 after MOUSEOVER set the target.
                useEntity(session, session.lastAttackTarget());
            }
        }
    }

    /**
     * Raw Interact wire when Cloudburst decode fails (common for OPEN_INVENTORY on modern clients).
     * Layout: unsigned byte action + unsigned varlong runtimeEntityId [+ optional mouse].
     */
    public static void onRawInteract(LinkBedrockSession session, int actionOrdinal, long runtimeEntityId) {
        if (session == null) {
            return;
        }
        InteractPacket.Action[] actions = InteractPacket.Action.values();
        if (actionOrdinal < 0 || actionOrdinal >= actions.length) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "be_Interact RAW unknown action=" + actionOrdinal);
            return;
        }
        InteractPacket.Action action = actions[actionOrdinal];
        BedrockJoinProbe.noteEvent(session.guid(),
                "be_Interact RAW action=" + action + " runtime=" + runtimeEntityId);
        InteractPacket pkt = new InteractPacket();
        pkt.setAction(action);
        pkt.setRuntimeEntityId(runtimeEntityId);
        onInteract(session, pkt);
    }

    public static void openPlayerInventory(LinkBedrockSession session) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        // If still marked open (missed close), force-close first so client accepts re-open.
        if (session.inventoryOpen()) {
            ContainerClosePacket force = new ContainerClosePacket();
            force.setId((byte) ContainerId.INVENTORY);
            force.setServerInitiated(true);
            session.sendUpstreamPacket(force);
            session.setInventoryOpen(false);
            BedrockJoinProbe.noteEvent(session.guid(), "be_OPEN_INVENTORY forceClose prior");
        }

        // Push last JE→BE snapshot (not air wipe) then open.
        JavaInventoryTranslator.pushPlayerInventory(session);

        ContainerOpenPacket open = new ContainerOpenPacket();
        open.setId((byte) ContainerId.INVENTORY);
        open.setType(ContainerType.INVENTORY);
        open.setBlockPosition(Vector3i.from(
                (int) Math.floor(session.posX()),
                (int) Math.floor(session.posY()),
                (int) Math.floor(session.posZ())));
        open.setUniqueEntityId(session.runtimeId());
        session.sendUpstreamPacket(open);
        session.setInventoryOpen(true);
        BedrockJoinProbe.noteEvent(session.guid(), "be_OPEN_INVENTORY→ContainerOpen");
        LOG.info("BE ContainerOpen INVENTORY user=" + session.username());
    }

    /**
     * Client closed a container — echo confirmation (Geyser) and clear open state so E works again.
     */
    public static void onContainerClose(LinkBedrockSession session, ContainerClosePacket close) {
        if (session == null || close == null) {
            return;
        }
        // Client wants close confirmation echoed upstream.
        session.sendUpstreamPacket(close);
        session.setInventoryOpen(false);
        session.clearOpenContainerSlots();
        JavaDownstreamClient down = session.downstream();
        if (down != null && down.phase() == JavaDownstreamClient.Phase.PLAY
                && session.lastJeWindowId() > 0) {
            down.sendContainerClose(session.lastJeWindowId());
            session.rememberJeWindow(-1, -1);
        }
        BedrockJoinProbe.noteEvent(session.guid(),
                "be_ContainerClose id=" + close.getId() + " echoed");
        LOG.fine("BE ContainerClose echoed id=" + close.getId() + " user=" + session.username());
    }

    /**
     * Animate decode failed (common: SwingSource optional on v898). Wire action 1 = SWING_ARM.
     * Shop taps are MOUSEOVER then swing — open with look-target.
     */
    public static void onRawAnimate(LinkBedrockSession session, int actionWire, long runtimeEntityId) {
        if (session == null) {
            return;
        }
        BedrockJoinProbe.noteEvent(session.guid(),
                "be_Animate RAW action=" + actionWire + " runtime=" + runtimeEntityId
                        + " lookTarget=" + session.lastAttackTarget());
        if (actionWire != 1) {
            return;
        }
        int target = session.lastAttackTarget();
        if (target <= 0 && runtimeEntityId > 0 && runtimeEntityId != session.runtimeId()) {
            target = (int) runtimeEntityId;
        }
        if (target > 0 && session.joinPhase() == LinkBedrockSession.JoinPhase.SPAWNED) {
            useEntity(session, target);
        }
    }

    /**
     * InventoryTransaction decode failed (usually item network-id registry). Pull type + runtime
     * when possible; otherwise open the MOUSEOVER look-target so NPC shops still work.
     */
    public static void onRawInventoryTransaction(LinkBedrockSession session, ByteBuf body) {
        if (session == null || body == null || !body.isReadable()) {
            return;
        }
        int reader = body.readerIndex();
        try {
            int legacyRequestId = VarInts.readInt(body);
            boolean hasLegacy = body.readBoolean();
            if (hasLegacy && legacyRequestId < -1 && (legacyRequestId & 1) == 0) {
                // Skip legacy slot array: count then (containerId + slot) pairs — best-effort.
                int count = VarInts.readUnsignedInt(body);
                for (int i = 0; i < count && body.isReadable(); i++) {
                    body.readByte();
                    VarInts.readInt(body);
                }
            }
            int typeOrd = VarInts.readUnsignedInt(body);
            InventoryTransactionType[] types = InventoryTransactionType.values();
            InventoryTransactionType type = typeOrd >= 0 && typeOrd < types.length
                    ? types[typeOrd] : null;
            BedrockJoinProbe.noteEvent(session.guid(),
                    "be_INV_TX RAW type=" + type + " ord=" + typeOrd
                            + " lookTarget=" + session.lastAttackTarget());
            if (type == InventoryTransactionType.ITEM_USE_ON_ENTITY) {
                // actions[] then runtimeEntityId — actions often fail without item defs, so
                // prefer look-target; try reading past an empty action list when possible.
                long runtime = 0L;
                int actionType = 0;
                if (body.isReadable()) {
                    int actionCount = VarInts.readUnsignedInt(body);
                    if (actionCount == 0 && body.isReadable()) {
                        runtime = VarInts.readUnsignedLong(body);
                        if (body.isReadable()) {
                            actionType = VarInts.readUnsignedInt(body);
                        }
                    }
                }
                if (runtime == 0L) {
                    runtime = session.lastAttackTarget();
                }
                if (runtime > 0L) {
                    if (actionType == 1) {
                        BedrockCombat.translateAttack(session, runtime);
                    } else {
                        useEntity(session, runtime);
                    }
                }
                return;
            }
            // Client sent INV_TX we could not fully classify — still try look-target shop open
            // when the player is clearly interacting with an NPC (MOUSEOVER set).
            if (session.lastAttackTarget() > 0
                    && session.joinPhase() == LinkBedrockSession.JoinPhase.SPAWNED) {
                useEntity(session, session.lastAttackTarget());
            }
        } catch (Exception e) {
            body.readerIndex(reader);
            if (session.lastAttackTarget() > 0
                    && session.joinPhase() == LinkBedrockSession.JoinPhase.SPAWNED) {
                BedrockJoinProbe.noteEvent(session.guid(),
                        "be_INV_TX RAW fallback lookTarget=" + session.lastAttackTarget()
                                + " err=" + e.getMessage());
                useEntity(session, session.lastAttackTarget());
            } else {
                throw e;
            }
        }
    }

    /** Right-click / NPC open → JE interact so PlayerInteractEntityEvent fires. */
    public static void useEntity(LinkBedrockSession session, long runtime) {
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "be_INTERACT skip use entity runtime=" + runtime
                            + " down=" + (down == null ? "null" : down.phase()));
            return;
        }
        int javaId = session.javaEntityForRuntime(runtime);
        if (javaId <= 0) {
            javaId = (int) runtime;
        }
        down.sendInteractUse(javaId, session.posX(),
                session.posY() + LinkBedrockSession.PLAYER_EYE_OFFSET, session.posZ());
        down.sendSwingArm(0);
        BedrockJoinProbe.noteEvent(session.guid(), "be_INTERACT→je use entity=" + javaId);
        LOG.info("BE interact use entity=" + javaId + " user=" + session.username());
    }

    /**
     * Seed empty windows only when no JE snapshot exists yet (join). Prefer real content after.
     */
    public static void ensureInventoryContents(LinkBedrockSession session) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        if (session.hasBedrockInventorySnapshot()) {
            JavaInventoryTranslator.pushPlayerInventory(session);
            BedrockJoinProbe.noteEvent(session.guid(), "be_InventoryContent push snapshot");
            return;
        }
        // First join before JE content: leave empty arrays; do not spam air wipe forever.
        session.storeBedrockInventory(air(36), air(4), org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR);
        JavaInventoryTranslator.pushPlayerInventory(session);
        BedrockJoinProbe.noteEvent(session.guid(), "be_InventoryContent seed empty");
    }

    private static org.cloudburstmc.protocol.bedrock.data.inventory.ItemData[] air(int n) {
        org.cloudburstmc.protocol.bedrock.data.inventory.ItemData[] a =
                new org.cloudburstmc.protocol.bedrock.data.inventory.ItemData[n];
        for (int i = 0; i < n; i++) {
            a[i] = org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.AIR;
        }
        return a;
    }
}

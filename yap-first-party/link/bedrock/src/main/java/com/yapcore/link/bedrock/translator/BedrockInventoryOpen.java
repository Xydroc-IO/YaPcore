package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.logging.Logger;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;

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
        if (action == InteractPacket.Action.OPEN_INVENTORY) {
            openPlayerInventory(session);
            return;
        }
        if (action == InteractPacket.Action.DAMAGE
                || action == InteractPacket.Action.INTERACT) {
            long runtime = interact.getRuntimeEntityId();
            int entityId = runtime != 0L ? (int) runtime : 0;
            if (entityId > 0) {
                session.noteAttackTarget(entityId);
            }
            if (action == InteractPacket.Action.DAMAGE && entityId > 0) {
                BedrockCombat.translateAttack(session, entityId);
            }
        }
        if (action == InteractPacket.Action.MOUSEOVER) {
            long runtime = interact.getRuntimeEntityId();
            if (runtime != 0L) {
                session.noteAttackTarget((int) runtime);
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
        BedrockJoinProbe.noteEvent(session.guid(),
                "be_ContainerClose id=" + close.getId() + " echoed");
        LOG.fine("BE ContainerClose echoed id=" + close.getId() + " user=" + session.username());
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

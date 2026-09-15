package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftRecipeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftRecipeOptionalAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;

/**
 * Forward Bedrock ItemStackRequest moves as JE {@code container_click} / result picks.
 */
final class BedrockItemStackJeForward {

    private BedrockItemStackJeForward() {
    }

    /**
     * Forward a simplified JE click so Folia applies the same move.
     * PICKUP on source then dest approximates take→place; SWAP uses mode=2.
     */
    static void forwardJeClick(LinkBedrockSession session,
                               ItemStackRequestSlotData source,
                               ItemStackRequestSlotData dest,
                               int count,
                               boolean swap) {
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            return;
        }
        int windowId = session.lastJeWindowId() > 0 ? session.lastJeWindowId() : 0;
        int stateId = Math.max(session.jeContainerStateId(), down.lastContainerStateId);
        Integer srcJe = BedrockItemStackSlots.toJeSlot(session, source);
        Integer dstJe = BedrockItemStackSlots.toJeSlot(session, dest);
        if (swap && srcJe != null && dstJe != null) {
            int button = 0;
            if (dstJe >= 36 && dstJe <= 44) {
                button = dstJe - 36;
            }
            down.sendContainerClick(windowId, stateId, srcJe, button, 2, null);
            return;
        }
        if (srcJe != null) {
            // Left-click pickup; Folia resyncs via container_set_* if counts differ.
            down.sendContainerClick(windowId, stateId, srcJe, 0, 0, null);
        }
        if (dstJe != null) {
            down.sendContainerClick(windowId, stateId, dstJe, 0, 0, null);
        } else if (srcJe != null && dest != null && dest.getContainer() == ContainerSlotType.CURSOR) {
            // Take → cursor: source click alone puts the stack on the JE carried slot.
            return;
        } else if (source != null && source.getContainer() == ContainerSlotType.CURSOR && dstJe == null
                && dest == null) {
            // Drop outside from cursor.
            down.sendContainerClick(windowId, stateId, -999, 0, 0, null);
        }
    }

    /** Click JE crafting / specialty result slot for the open menu. */
    static void applyCraftResultClick(LinkBedrockSession session, ItemStackRequestAction action) {
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            return;
        }
        int windowId = session.lastJeWindowId() > 0 ? session.lastJeWindowId() : 0;
        int stateId = Math.max(session.jeContainerStateId(), down.lastContainerStateId);
        int recipeNet = recipeNetworkId(action);
        if (recipeNet > 0) {
            // Specialty UIs (stonecutter/loom/smithing): pick recipe then take result.
            // Without a BE→JE recipe id map, a result-slot click still applies Folia's current
            // highlighted recipe after the client already selected it locally.
            BedrockJoinProbe.noteEvent(session.guid(),
                    "BE craft recipeNet=" + recipeNet + " → JE result click");
        }
        int resultSlot = BedrockItemStackSlots.jeResultSlot(session.lastJeMenuType());
        down.sendContainerClick(windowId, stateId, resultSlot, 0, 0, null);
        BedrockJoinProbe.noteEvent(session.guid(),
                "BE craft→JE click result win=" + windowId + " slot=" + resultSlot);
    }

    private static int recipeNetworkId(ItemStackRequestAction action) {
        if (action instanceof CraftRecipeOptionalAction opt) {
            return Math.max(0, opt.getRecipeNetworkId());
        }
        if (action instanceof CraftRecipeAction craft) {
            return Math.max(0, craft.getRecipeNetworkId());
        }
        return 0;
    }
}

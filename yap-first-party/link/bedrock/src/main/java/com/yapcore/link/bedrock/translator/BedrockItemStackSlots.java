package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.session.LinkBedrockSession;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;

/**
 * Bedrock inventory slot → JE window slot mapping for ItemStackRequest → container_click.
 *
 * <p>{@code null} means no JE click (e.g. cursor/carried — already implied by the other slot).
 */
final class BedrockItemStackSlots {

    private BedrockItemStackSlots() {
    }

    /** JE result slot index by MenuType id (see {@link JavaOpenScreenTranslator}). */
    static int jeResultSlot(int menuType) {
        return switch (menuType) {
            case 7 -> 2;  // anvil
            case 14 -> 2; // grindstone
            case 16 -> 3; // loom
            case 19 -> 2; // cartography
            case 20 -> 1; // stonecutter
            case 21 -> 3; // smithing
            case 9, 13, 18 -> 2; // blast / furnace / smoker
            default -> 0; // crafting + player craft (+ brewing bottles)
        };
    }

    /** Map Bedrock slot → JE window slot for player inv / open container. */
    static Integer toJeSlot(LinkBedrockSession session, ItemStackRequestSlotData slot) {
        if (slot == null) {
            return null;
        }
        ContainerSlotType type = slot.getContainer();
        int index = Math.max(0, slot.getSlot());
        if (type == ContainerSlotType.CURSOR) {
            // Carried item: JE applies via the other slot click; no dedicated cursor index.
            return null;
        }
        if (type == ContainerSlotType.HOTBAR) {
            return 36 + Math.min(8, index);
        }
        if (type == ContainerSlotType.HOTBAR_AND_INVENTORY) {
            if (index < 9) {
                return 36 + index;
            }
            if (index < 36) {
                return index;
            }
            return index;
        }
        if (type == ContainerSlotType.INVENTORY) {
            return 9 + Math.min(26, index);
        }
        if (type == ContainerSlotType.ARMOR) {
            return 5 + Math.min(3, index);
        }
        if (type == ContainerSlotType.OFFHAND) {
            return 45;
        }
        if (type == ContainerSlotType.CRAFTING_OUTPUT || type == ContainerSlotType.CREATED_OUTPUT) {
            return 0;
        }
        if (type == ContainerSlotType.CRAFTING_INPUT) {
            return 1 + index;
        }
        if (type == ContainerSlotType.ANVIL_INPUT) {
            return 0;
        }
        if (type == ContainerSlotType.ANVIL_MATERIAL) {
            return 1;
        }
        if (type == ContainerSlotType.ANVIL_RESULT) {
            return 2;
        }
        if (type == ContainerSlotType.STONECUTTER_INPUT) {
            return 0;
        }
        if (type == ContainerSlotType.STONECUTTER_RESULT) {
            return 1;
        }
        if (type == ContainerSlotType.GRINDSTONE_INPUT) {
            return 0;
        }
        if (type == ContainerSlotType.GRINDSTONE_ADDITIONAL) {
            return 1;
        }
        if (type == ContainerSlotType.GRINDSTONE_RESULT) {
            return 2;
        }
        if (type == ContainerSlotType.CARTOGRAPHY_INPUT) {
            return 0;
        }
        if (type == ContainerSlotType.CARTOGRAPHY_ADDITIONAL) {
            return 1;
        }
        if (type == ContainerSlotType.CARTOGRAPHY_RESULT) {
            return 2;
        }
        if (type == ContainerSlotType.LOOM_INPUT) {
            return 0;
        }
        if (type == ContainerSlotType.LOOM_DYE) {
            return 1;
        }
        if (type == ContainerSlotType.LOOM_MATERIAL) {
            return 2;
        }
        if (type == ContainerSlotType.LOOM_RESULT) {
            return 3;
        }
        if (type == ContainerSlotType.SMITHING_TABLE_TEMPLATE) {
            return 0;
        }
        if (type == ContainerSlotType.SMITHING_TABLE_INPUT) {
            return 1;
        }
        if (type == ContainerSlotType.SMITHING_TABLE_MATERIAL) {
            return 2;
        }
        if (type == ContainerSlotType.SMITHING_TABLE_RESULT) {
            return 3;
        }
        if (type == ContainerSlotType.FURNACE_INGREDIENT
                || type == ContainerSlotType.BLAST_FURNACE_INGREDIENT
                || type == ContainerSlotType.SMOKER_INGREDIENT) {
            return 0;
        }
        if (type == ContainerSlotType.FURNACE_FUEL) {
            return 1;
        }
        if (type == ContainerSlotType.FURNACE_RESULT) {
            return 2;
        }
        if (type == ContainerSlotType.ENCHANTING_INPUT) {
            return 0;
        }
        if (type == ContainerSlotType.ENCHANTING_MATERIAL) {
            return 1;
        }
        if (type == ContainerSlotType.BREWING_INPUT) {
            return Math.min(2, index);
        }
        if (type == ContainerSlotType.BREWING_FUEL) {
            return 3;
        }
        if (type == ContainerSlotType.BREWING_RESULT) {
            return Math.min(2, index);
        }
        if (type == ContainerSlotType.TRADE_INGREDIENT_1 || type == ContainerSlotType.TRADE2_INGREDIENT_1) {
            return 0;
        }
        if (type == ContainerSlotType.TRADE_INGREDIENT_2 || type == ContainerSlotType.TRADE2_INGREDIENT_2) {
            return 1;
        }
        if (type == ContainerSlotType.TRADE_RESULT || type == ContainerSlotType.TRADE2_RESULT) {
            return 2;
        }
        if (session != null && session.lastJeWindowId() > 0) {
            return index;
        }
        return index;
    }
}

package com.yapcore.crossplay.bedrock.inventory;

import com.yapcore.crossplay.bedrock.BedrockInventoryAuthority.Slot;

import java.util.Map;

/** Slot move/swap/consume operations on the shadow inventory. */
public final class InventorySlotOps {

    private InventorySlotOps() {
    }

    public static boolean isContainerIndex(int idx) {
        return idx >= BedrockInventoryLayout.CONTAINER_BASE && idx < BedrockInventoryLayout.TOTAL;
    }

    public static boolean isArmorOrCraft(int idx) {
        return (idx >= BedrockInventoryLayout.ARMOR_BASE
                && idx < BedrockInventoryLayout.ARMOR_BASE + BedrockInventoryLayout.ARMOR_SLOTS)
                || (idx >= BedrockInventoryLayout.CRAFT_BASE && idx <= BedrockInventoryLayout.CRAFT_RESULT);
    }

    public static boolean movePartial(Map<String, Slot[]> byUser, String username,
                                      int from, int to, int count) {
        if (from < 0 || to < 0 || from >= BedrockInventoryLayout.TOTAL
                || to >= BedrockInventoryLayout.TOTAL || count <= 0) {
            return false;
        }
        Slot[] slots = byUser.get(username.toLowerCase());
        Slot src = slots[from];
        if (src.isEmpty()) {
            return false;
        }
        int move = Math.min(count, src.count());
        Slot dst = slots[to];
        if (dst.isEmpty()) {
            slots[to] = new Slot(src.networkId(), move);
            slots[from] = src.withCount(src.count() - move);
            return true;
        }
        if (dst.networkId() != src.networkId()) {
            return false;
        }
        int room = 64 - dst.count();
        if (room <= 0) {
            return false;
        }
        move = Math.min(move, room);
        slots[to] = dst.withCount(dst.count() + move);
        slots[from] = src.withCount(src.count() - move);
        return true;
    }

    public static boolean swap(Map<String, Slot[]> byUser, String username, int from, int to) {
        if (from < 0 || to < 0 || from >= BedrockInventoryLayout.TOTAL
                || to >= BedrockInventoryLayout.TOTAL) {
            return false;
        }
        Slot[] slots = byUser.get(username.toLowerCase());
        Slot a = slots[from];
        slots[from] = slots[to];
        slots[to] = a;
        return true;
    }

    public static boolean consume(Map<String, Slot[]> byUser, String username, int from, int count) {
        if (from < 0 || from >= BedrockInventoryLayout.TOTAL || count <= 0) {
            return false;
        }
        Slot[] slots = byUser.get(username.toLowerCase());
        Slot src = slots[from];
        if (src.isEmpty()) {
            return false;
        }
        slots[from] = src.withCount(src.count() - Math.min(count, src.count()));
        return true;
    }

    public static void setSlot(Map<String, Slot[]> byUser, String username, int slot, Slot value) {
        if (slot < 0 || slot >= BedrockInventoryLayout.TOTAL) {
            return;
        }
        byUser.get(username.toLowerCase())[slot] = value == null ? Slot.AIR : value;
    }
}

package com.yapcore.abilities.bar;

import org.bukkit.inventory.ItemStack;

final class HotbarSnapshots {

    static final int HOTBAR_SIZE = 9;

    private HotbarSnapshots() {
    }

    static ItemStack[] capture(ItemStack[] source, int from, int count) {
        ItemStack[] out = new ItemStack[count];
        for (int i = 0; i < count; i++) {
            int idx = from + i;
            out[i] = idx < source.length && source[idx] != null ? source[idx].clone() : null;
        }
        return out;
    }

    static ItemStack[] captureHotbar(org.bukkit.inventory.PlayerInventory inv) {
        ItemStack[] out = new ItemStack[HOTBAR_SIZE];
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            out[i] = stack != null ? stack.clone() : null;
        }
        return out;
    }

    static void apply(org.bukkit.inventory.PlayerInventory inv, ItemStack[] stacks, int from, int count) {
        for (int i = 0; i < count; i++) {
            inv.setItem(from + i, stacks[i] != null ? stacks[i].clone() : null);
        }
    }

    static void applyHotbar(org.bukkit.inventory.PlayerInventory inv, ItemStack[] stacks) {
        apply(inv, stacks, 0, HOTBAR_SIZE);
    }

    static boolean allEmpty(ItemStack[] stacks) {
        if (stacks == null) {
            return true;
        }
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}

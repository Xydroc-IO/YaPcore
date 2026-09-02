package com.yapcore.playerdata.kit;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Survival kit definition (EssentialsX-class). */
public record KitDef(
        String id,
        long delaySeconds,
        int maxUses,
        double cost,
        boolean firstJoin,
        List<ItemStack> items,
        ItemStack helmet,
        ItemStack chestplate,
        ItemStack leggings,
        ItemStack boots,
        ItemStack offhand,
        List<String> commands
) {
    public KitDef {
        items = items == null ? List.of() : List.copyOf(items);
        commands = commands == null ? List.of() : List.copyOf(commands);
    }

    public int itemCount() {
        int n = items.size();
        if (helmet != null) {
            n++;
        }
        if (chestplate != null) {
            n++;
        }
        if (leggings != null) {
            n++;
        }
        if (boots != null) {
            n++;
        }
        if (offhand != null) {
            n++;
        }
        return n;
    }

    public ItemStack iconStack() {
        if (!items.isEmpty() && items.get(0) != null) {
            return items.get(0);
        }
        if (chestplate != null) {
            return chestplate;
        }
        if (helmet != null) {
            return helmet;
        }
        return null;
    }

    public boolean oneTime() {
        return maxUses == 1;
    }
}

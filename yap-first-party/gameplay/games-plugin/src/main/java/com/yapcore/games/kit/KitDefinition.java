package com.yapcore.games.kit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public record KitDefinition(String id, ItemStack[] armor, List<KitItem> items) {

    public record KitItem(int slot, ItemStack stack) {
    }

    public ItemStack[] buildInventory() {
        ItemStack[] contents = new ItemStack[36];
        for (KitItem item : items) {
            if (item.slot() >= 0 && item.slot() < contents.length) {
                contents[item.slot()] = item.stack().clone();
            }
        }
        return contents;
    }

    public static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
    }
}

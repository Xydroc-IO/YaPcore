package com.yapcore.mmocontent.boss;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record BossDefinition(
        String id,
        String displayName,
        org.bukkit.entity.EntityType entityType,
        double health,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        int respawnSeconds,
        List<LootEntry> loot
) {
    public record LootEntry(ItemStack item, String consoleCommand) {
    }
}

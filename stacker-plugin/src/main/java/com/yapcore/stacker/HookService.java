package com.yapcore.stacker;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * Soft hooks for Citizens / MythicMobs without hard compile deps.
 */
public final class HookService {

    private final StackerConfig config;
    private final boolean citizensPresent;
    private final boolean mythicPresent;

    public HookService(StackerConfig config) {
        this.config = config;
        this.citizensPresent = Bukkit.getPluginManager().getPlugin("Citizens") != null;
        this.mythicPresent = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
    }

    public boolean shouldSkip(Entity entity) {
        if (config.skipCitizens() && isCitizensNpc(entity)) {
            return true;
        }
        if (config.skipMythicMobs() && isMythicMob(entity)) {
            return true;
        }
        return false;
    }

    public boolean isCitizensNpc(Entity entity) {
        if (!citizensPresent) {
            return false;
        }
        return entity.hasMetadata("NPC");
    }

    public boolean isMythicMob(Entity entity) {
        if (!mythicPresent) {
            return false;
        }
        if (entity.hasMetadata("mythicmob") || entity.hasMetadata("MythicMob")) {
            return true;
        }
        for (String tag : entity.getScoreboardTags()) {
            if (tag != null && tag.toLowerCase().contains("mythic")) {
                return true;
            }
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        for (var key : pdc.getKeys()) {
            String ns = key.getNamespace();
            if (ns != null && (ns.contains("mythic") || ns.equals("mythicmobs"))) {
                return true;
            }
        }
        return false;
    }

    public boolean citizensPresent() {
        return citizensPresent;
    }

    public boolean mythicPresent() {
        return mythicPresent;
    }
}

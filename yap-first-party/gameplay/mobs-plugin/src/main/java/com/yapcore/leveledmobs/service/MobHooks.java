package com.yapcore.leveledmobs.service;

import com.yapcore.leveledmobs.LeveledMobsConfig;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Soft hooks — no hard deps on Mythic / Citizens / dungeons. */
public final class MobHooks {

    private static final NamespacedKey YAP_BOSS_ID = new NamespacedKey("yapcore", "yap_boss_id");
    private static final NamespacedKey DUNGEON_BOSS = new NamespacedKey("yapdungeons", "yap_dungeon_boss");
    private static final NamespacedKey DUNGEON_MOB = new NamespacedKey("yapdungeons", "yap_dungeon_mob");

    private final LeveledMobsConfig config;
    private final boolean citizensPresent;
    private final boolean mythicPresent;

    public MobHooks(LeveledMobsConfig config) {
        this.config = config;
        this.citizensPresent = Bukkit.getPluginManager().getPlugin("Citizens") != null;
        this.mythicPresent = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
    }

    public boolean shouldSkip(Entity entity) {
        if (config.skipYapBosses() && isYapBoss(entity)) {
            return true;
        }
        if (config.skipDungeon() && isDungeonMob(entity)) {
            return true;
        }
        if (config.skipCitizens() && isCitizensNpc(entity)) {
            return true;
        }
        if (config.skipMythic() && isMythicMob(entity)) {
            return true;
        }
        return false;
    }

    public boolean isYapBoss(Entity entity) {
        return entity.getPersistentDataContainer().has(YAP_BOSS_ID, PersistentDataType.STRING);
    }

    public boolean isDungeonMob(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.has(DUNGEON_BOSS, PersistentDataType.STRING)
                || pdc.has(DUNGEON_MOB, PersistentDataType.STRING);
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
}

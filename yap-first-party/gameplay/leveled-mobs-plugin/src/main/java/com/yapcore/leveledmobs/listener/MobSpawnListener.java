package com.yapcore.leveledmobs.listener;

import com.yapcore.leveledmobs.LeveledMobsConfig;
import com.yapcore.leveledmobs.LeveledMobsPlugin;
import com.yapcore.sched.YapSched;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

public final class MobSpawnListener implements Listener {

    private final LeveledMobsPlugin plugin;

    public MobSpawnListener(LeveledMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        LeveledMobsConfig cfg = plugin.leveledConfig();
        if (!cfg.enabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!eligible(entity, event.getSpawnReason())) {
            return;
        }
        if (plugin.store().hasLevel(entity)) {
            plugin.applier().reapplyStored(entity);
            return;
        }
        int level = plugin.calculator().calculate(entity);
        plugin.applier().applyNew(entity, level);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        LeveledMobsConfig cfg = plugin.leveledConfig();
        if (!cfg.enabled()) {
            return;
        }
        for (Entity e : event.getEntities()) {
            if (!(e instanceof LivingEntity living) || living instanceof Player) {
                continue;
            }
            if (!plugin.store().hasLevel(living)) {
                continue;
            }
            if (plugin.hooks().shouldSkip(living)) {
                continue;
            }
            YapSched.entity(plugin, living, () -> plugin.applier().reapplyStored(living));
        }
    }

    private boolean eligible(LivingEntity entity, CreatureSpawnEvent.SpawnReason reason) {
        if (entity instanceof Player) {
            return false;
        }
        LeveledMobsConfig cfg = plugin.leveledConfig();
        if (!cfg.worldAllowed(entity.getWorld().getName())) {
            return false;
        }
        if (!cfg.typeAllowed(entity.getType())) {
            return false;
        }
        if (!cfg.spawnReasonAllowed(reason)) {
            return false;
        }
        return !plugin.hooks().shouldSkip(entity);
    }
}

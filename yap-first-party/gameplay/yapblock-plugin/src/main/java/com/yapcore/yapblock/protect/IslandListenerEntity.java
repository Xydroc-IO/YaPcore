package com.yapcore.yapblock.protect;

import com.yapcore.yapblock.YapblockPlugin;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

final class IslandListenerEntity implements Listener {

    private final YapblockPlugin plugin;

    IslandListenerEntity(YapblockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        if (!(event.getEntity() instanceof Monster) && !(event.getEntity() instanceof Animals)) {
            return;
        }
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        if (!access.mobSpawnAllowed(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            if (!access.canBuild(player, event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
            return;
        }
        if (access.islandAt(event.getBlock().getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityHurt(EntityDamageByEntityEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        if (event.getDamager() instanceof Player player
                && !(event.getEntity() instanceof Player)
                && !access.canBuild(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }
}

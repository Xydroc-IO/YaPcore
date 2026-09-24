package com.yapcore.yapblock.protect;

import com.yapcore.yapblock.YapblockPlugin;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

final class IslandListenerExplosion implements Listener {

    private final YapblockPlugin plugin;

    IslandListenerExplosion(YapblockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        if (access.islandAt(event.getEntity().getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        var blocks = event.blockList().iterator();
        while (blocks.hasNext()) {
            Block block = blocks.next();
            if (access.islandAt(block.getLocation()).isPresent()) {
                blocks.remove();
            }
        }
        if (access.islandAt(event.getLocation()).isPresent() && event.blockList().isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        if (!access.fireAllowed(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        if (access == null) {
            return;
        }
        String name = event.getSource().getType().name();
        if ((name.contains("FIRE") || name.contains("LAVA"))
                && !access.fireAllowed(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}

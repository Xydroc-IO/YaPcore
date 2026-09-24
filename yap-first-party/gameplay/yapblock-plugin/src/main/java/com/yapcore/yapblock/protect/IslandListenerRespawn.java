package com.yapcore.yapblock.protect;

import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.YapblockPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Respawn on island home — void worlds have no usable vanilla spawn. */
final class IslandListenerRespawn implements Listener {

    private final YapblockPlugin plugin;

    IslandListenerRespawn(YapblockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        IslandAccess access = AccessLookup.access(plugin);
        var service = plugin.service();
        if (access == null || service == null) {
            return;
        }
        Player player = event.getPlayer();
        Location proposed = event.getRespawnLocation();
        // Keep bed / respawn-anchor when it sits on an enterable island with solid ground.
        if (proposed.getWorld() != null
                && access.isIslandWorld(proposed)
                && access.canEnter(player, proposed)
                && !isLikelyVoidSpawn(proposed)) {
            return;
        }

        IslandSnapshot snap = service.index().ofPlayer(player.getUniqueId()).orElse(null);
        if (snap == null) {
            Location hint = player.getLocation();
            if (access.isIslandWorld(hint)) {
                snap = access.islandAt(hint)
                        .filter(island -> {
                            World w = hint.getWorld();
                            if (w == null) {
                                return false;
                            }
                            return access.canEnter(player, service.index().homeLocation(w, island));
                        })
                        .orElse(null);
            }
        }
        if (snap == null) {
            return;
        }
        World world = plugin.getServer().getWorld(service.config().worldName());
        if (world == null) {
            world = proposed.getWorld();
        }
        if (world == null) {
            return;
        }
        event.setRespawnLocation(service.index().homeLocation(world, snap));
    }

    /** Vanilla / Essentials spawn in a void world is usually near 0,0 with nothing underfoot. */
    private static boolean isLikelyVoidSpawn(Location loc) {
        if (loc.getWorld() == null) {
            return true;
        }
        return Math.abs(loc.getBlockX()) < 2
                && Math.abs(loc.getBlockZ()) < 2
                && loc.getBlock().getRelative(0, -1, 0).getType().isAir();
    }
}

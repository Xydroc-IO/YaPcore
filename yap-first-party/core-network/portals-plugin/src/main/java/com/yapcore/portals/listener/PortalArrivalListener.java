package com.yapcore.portals.listener;

import com.yapcore.portals.PortalsConfig;
import com.yapcore.portals.store.PortalArrivalPending;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * After a fleet portal Connect, land the player on this backend's spawn
 * (YaPEssentials /setspawn when present, else world spawn).
 */
public final class PortalArrivalListener implements Listener {

    private final JavaPlugin plugin;
    private final PortalsConfig config;
    private final PortalArrivalPending pending;

    public PortalArrivalListener(JavaPlugin plugin, PortalsConfig config, PortalArrivalPending pending) {
        this.plugin = plugin;
        this.config = config;
        this.pending = pending;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!pending.consume(player.getUniqueId(), config.serverId())) {
            return;
        }
        // After PlayerData unfreeze / chunk load — short delay on entity thread.
        YapSched.entityLater(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location spawn = resolveSpawn(player);
            if (spawn == null || spawn.getWorld() == null) {
                plugin.getLogger().warning("Portal arrival for " + player.getName()
                        + " — no spawn resolved on " + config.serverId());
                return;
            }
            plugin.getLogger().info("Portal arrival " + player.getName() + " → "
                    + config.serverId() + " spawn "
                    + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ());
            player.teleportAsync(spawn).thenAccept(ok -> {
                if (Boolean.TRUE.equals(ok)) {
                    player.sendMessage("§7Arrived at §f" + config.serverId() + " §7spawn.");
                }
            });
        }, 25L);
    }

    private static Location resolveSpawn(Player player) {
        Location essentials = essentialsSpawn();
        if (essentials != null && essentials.getWorld() != null) {
            return essentials;
        }
        World world = player.getWorld();
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        return world == null ? null : world.getSpawnLocation().clone().add(0.5, 0, 0.5);
    }

    private static Location essentialsSpawn() {
        Plugin ess = Bukkit.getPluginManager().getPlugin("YaPEssentials");
        if (ess == null || !ess.isEnabled()) {
            return null;
        }
        try {
            Object store = ess.getClass().getMethod("spawnStore").invoke(ess);
            if (store == null) {
                return null;
            }
            Object loc = store.getClass().getMethod("spawn").invoke(store);
            return loc instanceof Location location ? location.clone() : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}

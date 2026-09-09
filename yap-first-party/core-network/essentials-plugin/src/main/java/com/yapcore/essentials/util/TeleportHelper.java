package com.yapcore.essentials.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia-safe teleports via {@link Player#teleportAsync(Location)}.
 */
public final class TeleportHelper {

    private TeleportHelper() {
    }

    public static void teleport(Plugin plugin, Player player, Location destination) {
        if (player == null || destination == null || destination.getWorld() == null) {
            return;
        }
        // Folia forbids sync Entity#teleport under region threading.
        player.teleportAsync(destination.clone());
    }
}

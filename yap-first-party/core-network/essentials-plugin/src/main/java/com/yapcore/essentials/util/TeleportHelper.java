package com.yapcore.essentials.util;

import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia-safe teleports via entity region scheduler.
 */
public final class TeleportHelper {

    private TeleportHelper() {
    }

    public static void teleport(Plugin plugin, Player player, Location destination) {
        if (player == null || destination == null || destination.getWorld() == null) {
            return;
        }
        Location dest = destination.clone();
        YapSched.entity(plugin, player, () -> player.teleport(dest));
    }
}

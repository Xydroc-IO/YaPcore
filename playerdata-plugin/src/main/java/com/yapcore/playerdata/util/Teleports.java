package com.yapcore.playerdata.util;

import com.yapcore.playerdata.db.LocationRow;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Teleport helpers that respect cross-server homes/warps. */
public final class Teleports {
    private Teleports() {
    }

    public static boolean tryTeleport(Player player, LocationRow row, String thisServerId) {
        if (!thisServerId.equals(row.serverId())) {
            player.sendMessage("§cThat location is on server §f" + row.serverId()
                    + "§c. Switch backends (Velocity), then try again.");
            return false;
        }
        World world = Bukkit.getWorld(row.world());
        if (world == null) {
            player.sendMessage("§cWorld §f" + row.world() + " §cis not loaded here.");
            return false;
        }
        Location loc = new Location(world, row.x(), row.y(), row.z(), row.yaw(), row.pitch());
        return player.teleport(loc);
    }

    public static LocationRow fromPlayer(Player player, String name, String serverId) {
        Location loc = player.getLocation();
        return new LocationRow(
                name,
                serverId,
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                player.getUniqueId());
    }
}

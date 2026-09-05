package com.yapcore.dungeons.service;

import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class LocationTeleport {

    private LocationTeleport() {
    }

    static void teleport(JavaPlugin plugin, Player player, LiveRun run) {
        Location ent = run.entrance();
        if (ent == null) {
            return;
        }
        YapSched.entity(plugin, player, () -> {
            player.teleport(ent);
            player.sendMessage("§aJoined dungeon L" + run.dungeonLevel());
        });
    }
}

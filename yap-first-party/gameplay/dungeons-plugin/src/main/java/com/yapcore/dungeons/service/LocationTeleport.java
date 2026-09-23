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
            player.teleportAsync(ent).thenAccept(ok -> YapSched.entity(plugin, player, () -> {
                if (Boolean.TRUE.equals(ok)) {
                    player.sendMessage("§aJoined dungeon L" + run.dungeonLevel());
                } else {
                    player.sendMessage("§cCould not teleport into the dungeon.");
                }
            }));
        });
    }
}

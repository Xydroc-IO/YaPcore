package com.yapcore.games.reset;

import com.yapcore.games.arena.ArenaDefinition;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArenaResetter {

    private final JavaPlugin plugin;
    private final boolean clearDrops;

    public ArenaResetter(JavaPlugin plugin, boolean clearDrops) {
        this.plugin = plugin;
        this.clearDrops = clearDrops;
    }

    public void clearDrops(ArenaDefinition arena) {
        if (!clearDrops) {
            return;
        }
        World world = plugin.getServer().getWorld(arena.worldName());
        if (world == null) {
            return;
        }
        int centerX = (arena.minX() + arena.maxX()) / 2;
        int centerZ = (arena.minZ() + arena.maxZ()) / 2;
        YapSched.region(plugin, world, centerX, centerZ, () -> {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Item)) {
                    continue;
                }
                Location loc = entity.getLocation();
                if (arena.contains(loc)) {
                    entity.remove();
                }
            }
        });
    }

    public void teleportToLobby(JavaPlugin plugin, Player player, ArenaDefinition arena) {
        if (arena.lobby() == null) {
            return;
        }
        org.bukkit.World world = plugin.getServer().getWorld(arena.worldName());
        if (world == null) {
            return;
        }
        Location lobby = arena.lobby().clone();
        lobby.setWorld(world);
        YapSched.entity(plugin, player, () -> player.teleport(lobby));
    }
}

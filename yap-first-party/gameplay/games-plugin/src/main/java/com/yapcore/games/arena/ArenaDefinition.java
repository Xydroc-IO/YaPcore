package com.yapcore.games.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

public record ArenaDefinition(
        String id,
        String worldName,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        List<Location> spawns,
        Location lobby) {

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public Location randomSpawn(World world, int index) {
        if (spawns.isEmpty()) {
            return lobby != null ? lobby.clone() : world.getSpawnLocation();
        }
        Location base = spawns.get(Math.floorMod(index, spawns.size()));
        return new Location(world, base.getX(), base.getY(), base.getZ(), base.getYaw(), base.getPitch());
    }
}

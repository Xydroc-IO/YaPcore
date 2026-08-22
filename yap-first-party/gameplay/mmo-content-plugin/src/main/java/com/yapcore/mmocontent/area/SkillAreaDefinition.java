package com.yapcore.mmocontent.area;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.List;

public record SkillAreaDefinition(
        String id,
        Type type,
        String world,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        int respawnSeconds,
        double xpMultiplier,
        List<OreNode> nodes
) {
    public enum Type {
        MINING_GUILD,
        FISHING
    }

    public record OreNode(int x, int y, int z, Material ore) {
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}

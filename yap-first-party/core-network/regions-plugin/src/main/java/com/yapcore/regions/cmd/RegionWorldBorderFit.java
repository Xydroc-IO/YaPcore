package com.yapcore.regions.cmd;

/**
 * Fits a vanilla square {@link org.bukkit.WorldBorder} to an inclusive XZ AABB.
 * Minecraft borders are always squares — non-square regions get the longer side as diameter.
 */
final class RegionWorldBorderFit {

    final double centerX;
    final double centerZ;
    final double size;

    RegionWorldBorderFit(double centerX, double centerZ, double size) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.size = size;
    }

    static RegionWorldBorderFit ofInclusive(int minX, int maxX, int minZ, int maxZ) {
        int x0 = Math.min(minX, maxX);
        int x1 = Math.max(minX, maxX);
        int z0 = Math.min(minZ, maxZ);
        int z1 = Math.max(minZ, maxZ);
        double centerX = (x0 + x1 + 1) / 2.0;
        double centerZ = (z0 + z1 + 1) / 2.0;
        double size = Math.max(x1 - x0 + 1, z1 - z0 + 1);
        return new RegionWorldBorderFit(centerX, centerZ, size);
    }
}

package com.yapcore.portals;

import org.bukkit.Location;

/** Axis-aligned block cuboid (inclusive). Immutable. */
public final class PortalCuboid {

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public PortalCuboid(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static PortalCuboid of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new PortalCuboid(x1, y1, z1, x2, y2, z2);
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public boolean containsBlock(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        return containsBlock(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public int volumeBlocks() {
        long w = (long) maxX - minX + 1L;
        long h = (long) maxY - minY + 1L;
        long d = (long) maxZ - minZ + 1L;
        long v = w * h * d;
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
    }

    @Override
    public String toString() {
        return "(" + minX + "," + minY + "," + minZ + ")-(" + maxX + "," + maxY + "," + maxZ + ")";
    }
}

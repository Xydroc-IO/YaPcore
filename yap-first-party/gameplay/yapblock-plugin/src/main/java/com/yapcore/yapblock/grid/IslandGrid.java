package com.yapcore.yapblock.grid;

/**
 * Spiral slot → (gx, gz) and world coordinates for fixed-distance island placement.
 * Slot 0 = (0,0), then right, up, left, down, expanding outward.
 */
public final class IslandGrid {

    private final int distance;
    private final int pasteY;

    public IslandGrid(int distance, int pasteY) {
        this.distance = Math.max(1, distance);
        this.pasteY = pasteY;
    }

    public int distance() {
        return distance;
    }

    public int pasteY() {
        return pasteY;
    }

    /** Convert spiral slot index to grid coordinates. */
    public int[] slotToGrid(int slot) {
        if (slot <= 0) {
            return new int[]{0, 0};
        }
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;
        int remaining = slot;
        for (int i = 0; i < slot * 4 + 4 && remaining > 0; i++) {
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int tmp = dx;
                dx = -dz;
                dz = tmp;
            }
            x += dx;
            z += dz;
            remaining--;
        }
        return new int[]{x, z};
    }

    public int worldX(int gridX) {
        return gridX * distance;
    }

    public int worldZ(int gridZ) {
        return gridZ * distance;
    }

    public String key(int gridX, int gridZ) {
        return gridX + ":" + gridZ;
    }

    /** Nearest island grid cell for a world location (not radius-aware). */
    public int[] worldToNearestGrid(double x, double z) {
        int gx = (int) Math.round(x / (double) distance);
        int gz = (int) Math.round(z / (double) distance);
        return new int[]{gx, gz};
    }
}

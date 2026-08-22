package com.yaplabs.yapengine.core.spatial;

/**
 * Optional fifth spatial region: a square chunk box around world origin (spawn/hub).
 * When enabled, chunks with {@code |cx|≤R && |cz|≤R} route to {@link SpatialQuadrant#SPAWN}
 * instead of NW/NE/SW/SE. Configured from Phase 3 flags — default off.
 */
public final class SpatialSpawnRegion {

    private static volatile boolean enabled;
    private static volatile int radiusChunks = 8;

    private SpatialSpawnRegion() {
    }

    public static void configure(boolean on, int radius) {
        enabled = on;
        radiusChunks = Math.max(1, radius);
    }

    public static boolean enabled() {
        return enabled;
    }

    public static int radiusChunks() {
        return radiusChunks;
    }

    public static boolean containsChunk(int chunkX, int chunkZ) {
        if (!enabled) {
            return false;
        }
        int r = radiusChunks;
        return chunkX >= -r && chunkX <= r && chunkZ >= -r && chunkZ <= r;
    }
}

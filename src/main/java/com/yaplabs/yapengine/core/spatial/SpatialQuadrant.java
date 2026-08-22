package com.yaplabs.yapengine.core.spatial;

/**
 * Four cardinal spatial loops (Threads 3–6) plus optional {@link #SPAWN} (dedicated
 * hub/spawn worker when {@link SpatialSpawnRegion} is enabled).
 * Prefer {@link BitwiseQuadrantIndex} for hot-path location → thread mapping.
 */
public enum SpatialQuadrant {
    NW(0, "yap-t3-spatial-nw"),
    NE(1, "yap-t4-spatial-ne"),
    SW(2, "yap-t5-spatial-sw"),
    SE(3, "yap-t6-spatial-se"),
    /** Origin spawn/hub box — only used when {@link SpatialSpawnRegion#enabled()}. */
    SPAWN(4, "yap-spatial-spawn");

    private static final SpatialQuadrant[] BY_ID = {NW, NE, SW, SE, SPAWN};

    private final int id;
    private final String threadName;

    SpatialQuadrant(int id, String threadName) {
        this.id = id;
        this.threadName = threadName;
    }

    public int index() {
        return id;
    }

    public int id() {
        return id;
    }

    public String threadName() {
        return threadName;
    }

    public static SpatialQuadrant byId(int id) {
        if (id >= 0 && id < BY_ID.length) {
            return BY_ID[id];
        }
        return BY_ID[id & 3];
    }

    /** Hot path — delegates to bitwise / spawn-box index. */
    public static SpatialQuadrant fromCoordinates(int blockX, int blockZ) {
        return BitwiseQuadrantIndex.fromBlock(blockX, blockZ);
    }
}

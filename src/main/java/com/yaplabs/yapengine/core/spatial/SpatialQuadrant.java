package com.yaplabs.yapengine.core.spatial;

/**
 * Four spatial game-loop assignments (Threads 3–6).
 * Prefer {@link BitwiseQuadrantIndex} for hot-path location → thread mapping.
 */
public enum SpatialQuadrant {
    NW(0, "yap-t3-spatial-nw"),
    NE(1, "yap-t4-spatial-ne"),
    SW(2, "yap-t5-spatial-sw"),
    SE(3, "yap-t6-spatial-se");

    private static final SpatialQuadrant[] BY_ID = {NW, NE, SW, SE};

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
        return BY_ID[id & 3];
    }

    /** Hot path — delegates to bitwise index (&lt;1 ns). */
    public static SpatialQuadrant fromCoordinates(int blockX, int blockZ) {
        return BitwiseQuadrantIndex.fromBlock(blockX, blockZ);
    }
}

package com.yapcore.paper.phase3;

/**
 * Cached Phase 3 / 3.5 JVM flags — avoids synchronized {@code Boolean.getBoolean}
 * on every chunk/entity tick.
 */
public final class YapPhase3Flags {

    private static volatile boolean spatialTick;
    private static volatile boolean spatialBlockFluid;
    private static volatile boolean spatialRandom;
    private static volatile boolean spatialBlockEntities;
    private static volatile boolean spatialRedstone;
    private static volatile boolean flushing;

    private YapPhase3Flags() {
    }

    public static void refresh() {
        spatialTick = Boolean.getBoolean("yapcore.phase3.spatial-tick");
        spatialBlockFluid = Boolean.getBoolean("yapcore.phase3.spatial-blockfluid");
        spatialRandom = Boolean.getBoolean("yapcore.phase3.spatial-random");
        spatialBlockEntities = Boolean.getBoolean("yapcore.phase3.spatial-blockentities");
        spatialRedstone = Boolean.getBoolean("yapcore.phase3.spatial-redstone");
        flushing = Boolean.getBoolean("yapcore.phase3.spatial-tick.flushing");
    }

    public static boolean spatialTick() {
        return spatialTick;
    }

    public static boolean spatialBlockFluid() {
        return spatialBlockFluid;
    }

    public static boolean spatialRandom() {
        return spatialRandom;
    }

    public static boolean spatialBlockEntities() {
        return spatialBlockEntities;
    }

    public static boolean spatialRedstone() {
        return spatialRedstone;
    }

    public static boolean flushing() {
        return flushing;
    }

    public static void setFlushing(boolean value) {
        flushing = value;
        if (value) {
            System.setProperty("yapcore.phase3.spatial-tick.flushing", "true");
        } else {
            System.clearProperty("yapcore.phase3.spatial-tick.flushing");
        }
    }
}

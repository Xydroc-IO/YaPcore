package com.yapcore.paper.phase3;

import com.yaplabs.yapengine.core.spatial.SpatialSpawnRegion;

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
    private static volatile boolean spatialBorders;
    private static volatile boolean spatialTracker;
    private static volatile boolean spatialTrackerSkipClean;
    private static volatile boolean spatialTrackerPlayers;
    private static volatile boolean spatialCoalesceBarriers;
    private static volatile boolean spatialEntityActivation;
    private static volatile boolean spatialDistantBrain;
    private static volatile boolean spatialSpawn;
    private static volatile int spawnRadiusChunks;
    private static volatile int distantBrainStartBlocks;
    private static volatile int distantBrainFarBlocks;
    private static volatile int distantBrainMaxInterval;
    private static volatile boolean flushing;

    private YapPhase3Flags() {
    }

    public static void refresh() {
        spatialTick = Boolean.getBoolean("yapcore.phase3.spatial-tick");
        spatialBlockFluid = Boolean.getBoolean("yapcore.phase3.spatial-blockfluid");
        spatialRandom = Boolean.getBoolean("yapcore.phase3.spatial-random");
        spatialBlockEntities = Boolean.getBoolean("yapcore.phase3.spatial-blockentities");
        spatialRedstone = Boolean.getBoolean("yapcore.phase3.spatial-redstone");
        spatialBorders = Boolean.getBoolean("yapcore.phase3.spatial-borders");
        spatialTracker = Boolean.getBoolean("yapcore.phase3.spatial-tracker");
        String skipClean = System.getProperty("yapcore.phase3.spatial-tracker-skip-clean");
        spatialTrackerSkipClean = skipClean == null || Boolean.parseBoolean(skipClean);
        // Phase 3.12 — player sendChanges on spatial after main moonrise$tick (tick stays main).
        String trackerPlayers = System.getProperty("yapcore.phase3.spatial-tracker-players");
        spatialTrackerPlayers = trackerPlayers == null || Boolean.parseBoolean(trackerPlayers);
        String coalesce = System.getProperty("yapcore.phase3.spatial-coalesce-barriers");
        spatialCoalesceBarriers = coalesce == null || Boolean.parseBoolean(coalesce);
        String ear = System.getProperty("yapcore.phase3.spatial-entity-activation");
        spatialEntityActivation = ear == null || Boolean.parseBoolean(ear);
        String brain = System.getProperty("yapcore.phase3.spatial-distant-brain");
        spatialDistantBrain = brain == null || Boolean.parseBoolean(brain);
        distantBrainStartBlocks = intProp("yapcore.phase3.distant-brain-start", 24);
        distantBrainFarBlocks = intProp("yapcore.phase3.distant-brain-far", 80);
        distantBrainMaxInterval = intProp("yapcore.phase3.distant-brain-interval", 20);
        // Opt-in: fifth SPAWN spatial worker for hub/spawn box (default off).
        spatialSpawn = Boolean.getBoolean("yapcore.phase3.spatial-spawn");
        spawnRadiusChunks = intProp("yapcore.phase3.spawn-radius-chunks", 8);
        SpatialSpawnRegion.configure(spatialSpawn, spawnRadiusChunks);
        flushing = Boolean.getBoolean("yapcore.phase3.spatial-tick.flushing");
    }

    private static int intProp(String key, int def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
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

    /** Border chunk entity / TE / redstone tick on T8 under DLM leases. */
    public static boolean spatialBorders() {
        return spatialBorders;
    }

    /**
     * Non-player {@code ServerEntity.sendChanges} (+ moonrise$tick) on spatial cores / T8.
     * Track/untrack stay on Paper main. Default on for high-pop product.
     */
    public static boolean spatialTracker() {
        return spatialTracker;
    }

    /**
     * Phase 3.9 — skip queueing/running {@code sendChanges} when the tracker has
     * nothing to emit (no dirty data / not on update interval). Does <em>not</em>
     * move player tick off main.
     */
    public static boolean spatialTrackerSkipClean() {
        return spatialTrackerSkipClean;
    }

    /**
     * Phase 3.12 — after {@code moonrise$tick} on Paper main, offer player
     * {@code ServerEntity.sendChanges} to spatial cores (export only; tick + events stay main).
     * Requires {@link #spatialTracker()}. Kill switch:
     * {@code -Dyapcore.phase3.spatial-tracker-players=false}.
     */
    public static boolean spatialTrackerPlayers() {
        return spatialTrackerPlayers;
    }

    /** Merge block-events into entity/BE flush — fewer {@code runParallelTick} barriers. */
    public static boolean spatialCoalesceBarriers() {
        return spatialCoalesceBarriers;
    }

    /** Paper {@code ActivationRange} on spatial entity tick (players never offered). */
    public static boolean spatialEntityActivation() {
        return spatialEntityActivation;
    }

    /** First-party distant path/AI throttle (Leaf DAB–class, YaP code). */
    public static boolean spatialDistantBrain() {
        return spatialDistantBrain;
    }

    /**
     * Fifth spatial region: origin spawn/hub box on its own worker.
     * Does not move players off Paper main — only non-player interior work in the box.
     */
    public static boolean spatialSpawn() {
        return spatialSpawn;
    }

    public static int spawnRadiusChunks() {
        return spawnRadiusChunks;
    }

    public static int distantBrainStartBlocks() {
        return distantBrainStartBlocks;
    }

    public static int distantBrainFarBlocks() {
        return distantBrainFarBlocks;
    }

    public static int distantBrainMaxInterval() {
        return distantBrainMaxInterval;
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

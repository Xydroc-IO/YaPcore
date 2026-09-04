package com.yapcore.bench;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

/**
 * Folia-safe load inject + snapshots. Chunk/world mutation must run on the owning
 * region thread — never from {@code GlobalRegionScheduler}.
 */
final class BenchRegionLoad {

    private BenchRegionLoad() {
    }

    record LoadSnapshot(int tntAlive, double fuseMean, int hoppers, int entitiesTotal,
                        int players, int villagers, int loadedChunks, String entityTop) {
    }

    /** Region-safe highpop/fullcite world fixtures + optional deep TNT (fullcite). */
    static void preparePopBench(JavaPlugin plugin, World world, String scenario,
                                Consumer<Integer> onReady) {
        BenchRegionLoadSetup.preparePopBench(plugin, world, scenario, onReady);
    }

    /** Region-safe prepare for idle|entity|farm|heavypop|spawncollapse. {@code onReady} gets expected TNT. */
    static void prepare(JavaPlugin plugin, World world, String scenario, Consumer<Integer> onReady) {
        BenchRegionLoadSetup.prepare(plugin, world, scenario, onReady);
    }

    static void snapshotAsync(JavaPlugin plugin, World world, Consumer<LoadSnapshot> cb) {
        BenchRegionLoadLoops.snapshotAsync(plugin, world, cb);
    }

    static String fmt(double v) {
        return BenchRegionLoadLoops.fmt(v);
    }
}

package com.yapcore.bench;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

final class BenchSampler {
    private final JavaPlugin plugin;
    private final BenchWorldPrep worldPrep;
    /** Fair split cite: hottest region MSPT, not the (0,0) shard after a cut. */
    private String msptAggregation = "single_region";
    private int reportedSampleCxWest;
    private int reportedSampleCxEast;

    BenchSampler(JavaPlugin plugin, BenchWorldPrep worldPrep) {
        this.plugin = plugin;
        this.worldPrep = worldPrep;
    }

    record LoadSnapshot(int tntAlive, double fuseMean, int hoppers, int entitiesTotal,
                                int players, int villagers, int loadedChunks,
                                String entityTop) {
    }

    private LoadSnapshot snapshotLoad(World world) {
        int tnt = 0;
        long fuseSum = 0;
        int hoppers = 0;
        int entities = 0;
        int villagers = 0;
        java.util.Map<String, Integer> byType = new java.util.HashMap<>();
        for (Entity e : world.getEntities()) {
            entities++;
            String key = e.getType().name();
            byType.merge(key, 1, Integer::sum);
            if (e instanceof TNTPrimed tntPrimed) {
                tnt++;
                fuseSum += tntPrimed.getFuseTicks();
            }
            if (e instanceof Villager) {
                villagers++;
            }
        }
        String entityTop = byType.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(12)
                .map(en -> en.getKey() + "=" + en.getValue())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        for (int[][] piles : BenchWorldPrep.HEAVY_PILES) {
            int[] c = piles[0];
            var chunk = world.getChunkAt(c[0], c[1]);
            if (!chunk.isLoaded()) {
                continue;
            }
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Hopper) {
                    hoppers++;
                }
            }
        }
        // highpop / near-spawn fixtures
        for (int[] c : BenchWorldPrep.INTERIOR) {
            var chunk = world.getChunkAt(c[0], c[1]);
            if (!chunk.isLoaded()) {
                continue;
            }
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Hopper) {
                    hoppers++;
                }
            }
        }
        double fuseMean = tnt == 0 ? 0.0 : (double) fuseSum / tnt;
        return new LoadSnapshot(tnt, fuseMean, hoppers, entities,
                Bukkit.getOnlinePlayers().size(), villagers, world.getLoadedChunks().length,
                entityTop);
    }

    void sampleAndWrite(World world, String scenario, String label, String out,
                                int warmupSec, int sampleSec, int expectedTnt) {
        if (YapSched.isRegionized()) {
            BenchRegionLoad.snapshotAsync(plugin, world, start -> {
                plugin.getLogger().info("Load@sample-start players=" + start.players()
                        + " entities=" + start.entitiesTotal()
                        + " villagers=" + start.villagers()
                        + " hoppers=" + start.hoppers()
                        + " chunks=" + start.loadedChunks()
                        + " tnt=" + start.tntAlive()
                        + " types=" + start.entityTop());
                runSampler(world, scenario, label, out, warmupSec, sampleSec, expectedTnt,
                        toLegacySnapshot(start));
            });
            return;
        }
        LoadSnapshot start = snapshotLoad(world);
        plugin.getLogger().info("Load@sample-start players=" + start.players()
                + " entities=" + start.entitiesTotal()
                + " villagers=" + start.villagers()
                + " hoppers=" + start.hoppers()
                + " chunks=" + start.loadedChunks()
                + " tnt=" + start.tntAlive()
                + " types=" + start.entityTop());
        runSampler(world, scenario, label, out, warmupSec, sampleSec, expectedTnt, start);
    }

    static LoadSnapshot toLegacySnapshot(BenchRegionLoad.LoadSnapshot s) {
        return new LoadSnapshot(s.tntAlive(), s.fuseMean(), s.hoppers(), s.entitiesTotal(),
                s.players(), s.villagers(), s.loadedChunks(), s.entityTop());
    }

    void runSampler(World world, String scenario, String label, String out,
                            int warmupSec, int sampleSec, int expectedTnt, LoadSnapshot start) {
        List<Double> mspt = new ArrayList<>();
        List<Double> tps1m = new ArrayList<>();
        final int[] left = {sampleSec};
        final YapTask[] sampler = new YapTask[1];
        // Seconds into sample when to fire /save-all once (−1 = disabled). Used by async-save smoke.
        final int saveAllAt = Integer.getInteger("yap.bench.save_all_at", -1);
        final int[] saveFiredAt = {-1}; // sample index when save-all ran
        // Folia getAverageTickTime() is region-local. After a packed-spawn cut the
        // (0,0) shard is only the +X side — citing that vs stock's one blob is a fake win.
        // Packed fullcite/highpop samples kept-edge west (−12) and east (4), max MSPT / min TPS.
        final int lobes = Math.max(1, Integer.getInteger("yap.bench.lobes", 1));
        final int lobeOffset = Math.max(16, Integer.getInteger("yap.bench.lobe_offset_chunks", 40));
        final int stripHalf = Integer.getInteger("yap.bench.strip_half_width", 0);
        final boolean packedSplitSample = YapSched.isRegionized()
                && stripHalf <= 0
                && lobes < 2
                && ("fullcite".equals(scenario) || "highpop".equals(scenario));
        final boolean multiSample = lobes >= 2 || stripHalf > 0 || packedSplitSample;
        final int sampleCx = 0;
        final int sampleCz = 0;
        final int sampleCxEast = stripHalf > 0
                ? Math.max(8, stripHalf - 4)
                : (lobes >= 2 ? lobeOffset : (packedSplitSample ? 4 : 0));
        final int sampleCxWest = packedSplitSample ? -12 : -sampleCxEast;
        msptAggregation = multiSample ? "max_region" : "single_region";
        reportedSampleCxWest = sampleCxWest;
        reportedSampleCxEast = sampleCxEast;
        final double[] lastEast = {Double.NaN};
        final double[] lastWest = {Double.NaN};
        final double[] lastEastTps = {Double.NaN};
        final double[] lastWestTps = {Double.NaN};
        Runnable tick = () -> {
            int elapsed = sampleSec - left[0];
            if (saveAllAt >= 0 && saveFiredAt[0] < 0 && elapsed >= saveAllAt) {
                try {
                    // Dirty a few blocks so save-all has real Moonrise flush work.
                    int y = world.getMinHeight() + 4;
                    for (int dx = 0; dx < 8; dx++) {
                        for (int dz = 0; dz < 8; dz++) {
                            var block = world.getBlockAt(dx, y, dz);
                            block.setType(block.getType(), false);
                        }
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
                    saveFiredAt[0] = mspt.size();
                    plugin.getLogger().info("Bench save-all at sample_elapsed=" + elapsed
                            + "s index=" + saveFiredAt[0]);
                } catch (Throwable t) {
                    plugin.getLogger().warning("save-all failed: " + t.getMessage());
                    saveFiredAt[0] = mspt.size();
                }
            }
            try {
                if (multiSample) {
                    // Both sides required — one shard vs stock blob is the 33% fake win.
                    double a = lastWest[0];
                    double b = lastEast[0];
                    if (!Double.isNaN(a) && !Double.isNaN(b)) {
                        mspt.add(Math.max(a, b));
                    }
                } else {
                    mspt.add(Bukkit.getServer().getAverageTickTime());
                }
            } catch (UnsupportedOperationException uoe) {
                // Not on a region thread somehow — skip sample
                return;
            }
            double regionTps = regionTpsAt(world, sampleCx, sampleCz);
            if (multiSample) {
                double westTps = lastWestTps[0];
                double eastTps = lastEastTps[0];
                if (!Double.isNaN(westTps) && !Double.isNaN(eastTps)) {
                    regionTps = Math.min(westTps, eastTps);
                }
            }
            try {
                double[] rt = Bukkit.getServer().getTPS();
                tps1m.add(!Double.isNaN(regionTps) ? regionTps : (rt.length > 0 ? rt[0] : 0));
            } catch (Throwable t) {
                tps1m.add(!Double.isNaN(regionTps) ? regionTps : 0);
            }
            left[0]--;
            if (left[0] <= 0) {
                sampler[0].cancel();
                if (YapSched.isRegionized()) {
                    BenchRegionLoad.snapshotAsync(plugin, world, endR -> {
                        LoadSnapshot end = toLegacySnapshot(endR);
                        plugin.getLogger().info("Load@sample-end players=" + end.players()
                                + " entities=" + end.entitiesTotal()
                                + " chunks=" + end.loadedChunks());
                        writeJson(scenario, label, out, warmupSec, sampleSec, mspt, tps1m,
                                expectedTnt, start, end, saveAllAt, saveFiredAt[0]);
                        plugin.getLogger().info("Bench complete — shutting down");
                        YapSched.regionChunkLater(plugin, world, 0, 0, Bukkit::shutdown, 20L);
                    });
                } else {
                    LoadSnapshot end = snapshotLoad(world);
                    plugin.getLogger().info("Load@sample-end players=" + end.players()
                            + " entities=" + end.entitiesTotal()
                            + " chunks=" + end.loadedChunks());
                    writeJson(scenario, label, out, warmupSec, sampleSec, mspt, tps1m,
                            expectedTnt, start, end, saveAllAt, saveFiredAt[0]);
                    plugin.getLogger().info("Bench complete — shutting down");
                    YapSched.regionChunkLater(plugin, world, 0, 0, Bukkit::shutdown, 20L);
                }
            }
        };
        if (YapSched.isRegionized() && multiSample) {
            plugin.getLogger().info("MSPT sampler dual chunks (" + sampleCxWest + ",0) & ("
                    + sampleCxEast + ",0) — max region MSPT / min region TPS (not one shard vs blob)");
            BenchRegionLoadLoops.pinChunk(plugin, world, sampleCxWest, sampleCz);
            BenchRegionLoadLoops.pinChunk(plugin, world, sampleCxEast, sampleCz);
            YapSched.regionChunkTimer(plugin, world, sampleCxWest, sampleCz, () -> {
                try {
                    lastWest[0] = Bukkit.getServer().getAverageTickTime();
                    lastWestTps[0] = regionTpsAt(world, sampleCxWest, sampleCz);
                } catch (UnsupportedOperationException ignored) {
                    // not owning thread
                }
            }, 20L, 20L);
            YapSched.regionChunkTimer(plugin, world, sampleCxEast, sampleCz, () -> {
                try {
                    lastEast[0] = Bukkit.getServer().getAverageTickTime();
                    lastEastTps[0] = regionTpsAt(world, sampleCxEast, sampleCz);
                } catch (UnsupportedOperationException ignored) {
                    // not owning thread
                }
            }, 20L, 20L);
            // Region 1 Hz combiner — global scheduler can stall under aligned soft-wave.
            sampler[0] = YapSched.regionChunkTimer(plugin, world, sampleCx, sampleCz, tick, 20L, 20L);
        } else if (YapSched.isRegionized()) {
            plugin.getLogger().info("MSPT sampler on region chunk (" + sampleCx + "," + sampleCz
                    + ") — Folia region-local getAverageTickTime()");
            sampler[0] = YapSched.regionChunkTimer(plugin, world, sampleCx, sampleCz, tick, 20L, 20L);
        } else {
            sampler[0] = YapSched.globalTimer(plugin, tick, 20L, 20L);
        }
    }

    void writeJson(String scenario, String label, String outPath,
                           int warmup, int sampleSec, List<Double> mspt, List<Double> tps,
                           int expectedTnt, LoadSnapshot start, LoadSnapshot end) {
        writeJson(scenario, label, outPath, warmup, sampleSec, mspt, tps,
                expectedTnt, start, end, -1, -1);
    }

    void writeJson(String scenario, String label, String outPath,
                           int warmup, int sampleSec, List<Double> mspt, List<Double> tps,
                           int expectedTnt, LoadSnapshot start, LoadSnapshot end,
                           int saveAllAt, int saveFiredAt) {
        BenchSamplerReport.write(plugin, msptAggregation, reportedSampleCxWest, reportedSampleCxEast,
                scenario, label, outPath, warmup, sampleSec, mspt, tps,
                expectedTnt, start, end, saveAllAt, saveFiredAt);
    }

    static double regionTpsAt(World world, int chunkX, int chunkZ) {
        try {
            java.lang.reflect.Method m = Bukkit.getServer().getClass()
                    .getMethod("getRegionTPS", org.bukkit.World.class, int.class, int.class);
            Object regObj = m.invoke(Bukkit.getServer(), world, chunkX, chunkZ);
            if (regObj instanceof double[] reg && reg.length > 0) {
                return reg[0];
            }
        } catch (ReflectiveOperationException ignored) {
            // Paper API without Folia region TPS
        }
        return Double.NaN;
    }
}

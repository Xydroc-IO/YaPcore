package com.yapcore.bench;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Folia-safe chunk iteration, clear, inject loops, and load snapshots. */
final class BenchRegionLoadLoops {

    private BenchRegionLoadLoops() {
    }

    static final int[][] INTERIOR = {
            {2, 2}, {-3, 2}, {2, -3}, {-3, -3}
    };

    static final int[][][] HEAVY_PILES = {
            {{8, 8}, {12, 12}, {8, 12}, {12, 8}},
            {{-9, 8}, {-13, 12}, {-9, 12}, {-13, 8}},
            {{8, -9}, {12, -13}, {8, -13}, {12, -9}},
            {{-9, -9}, {-13, -13}, {-9, -13}, {-13, -9}},
    };

    static final int MAX_TNT_PER_CHUNK = 600;

    static Set<long[]> interestChunks() {
        return interestChunks(System.getProperty("yap.bench.scenario", ""));
    }

    static Set<long[]> interestChunks(String scenario) {
        if ("spawncollapse".equals(scenario)) {
            return BenchRegionSpawnChunks.spawnCollapseChunks();
        }
        Set<long[]> out = new LinkedHashSet<>();
        for (int[] c : INTERIOR) {
            out.add(pack(c[0], c[1]));
        }
        for (int[][] piles : HEAVY_PILES) {
            for (int[] c : piles) {
                out.add(pack(c[0], c[1]));
            }
        }
        return out;
    }

    static long[] pack(int cx, int cz) {
        return new long[]{cx, cz};
    }

    static void forEachChunk(JavaPlugin plugin, World world, Set<long[]> chunks,
                             Consumer<long[]> perChunk, Runnable onDone) {
        if (chunks.isEmpty()) {
            YapSched.global(plugin, onDone);
            return;
        }
        AtomicInteger left = new AtomicInteger(chunks.size());
        for (long[] c : chunks) {
            int cx = (int) c[0];
            int cz = (int) c[1];
            YapSched.regionChunk(plugin, world, cx, cz, () -> {
                try {
                    perChunk.accept(c);
                } finally {
                    if (left.decrementAndGet() == 0) {
                        YapSched.global(plugin, onDone);
                    }
                }
            });
        }
    }

    static void clearInterest(JavaPlugin plugin, World world, Runnable onDone) {
        clearInterest(plugin, world, interestChunks(), onDone);
    }

    static void clearInterest(JavaPlugin plugin, World world, Set<long[]> chunks, Runnable onDone) {
        // Folia: force-load flags are global-region only; chunk load/spawn is region-owned.
        YapSched.global(plugin, () -> {
            for (long[] c : chunks) {
                world.setChunkForceLoaded((int) c[0], (int) c[1], true);
            }
            forEachChunk(plugin, world, chunks, c -> {
                int cx = (int) c[0];
                int cz = (int) c[1];
                world.getChunkAt(cx, cz).load(true);
                for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
                    if (!(e instanceof Player)) {
                        e.remove();
                    }
                }
            }, onDone);
        });
    }

    static void injectFarm(JavaPlugin plugin, World world, Runnable onDone) {
        Set<long[]> chunks = new LinkedHashSet<>();
        for (int[] c : INTERIOR) {
            chunks.add(pack(c[0], c[1]));
        }
        forEachChunk(plugin, world, chunks, c -> {
            int cx = (int) c[0];
            int cz = (int) c[1];
            int bx = cx << 4;
            int bz = cz << 4;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int y = world.getHighestBlockYAt(bx + x, bz + z);
                    world.getBlockAt(bx + x, y, bz + z).setType(Material.FARMLAND);
                    world.getBlockAt(bx + x, y + 1, bz + z).setType(Material.WHEAT);
                }
            }
        }, () -> {
            plugin.getLogger().info("Planted wheat farms in 4 interior quads (region)");
            onDone.run();
        });
    }

    static void injectTntAndHoppers(JavaPlugin plugin, World world, String scenario,
                                            Consumer<Integer> onReady) {
        int entities = Integer.getInteger("yap.bench.entities",
                switch (scenario) {
                    case "entity" -> 120;
                    case "fullcite" -> 600;
                    default -> 1200;
                });
        int hoppers = "fullcite".equals(scenario)
                ? Integer.getInteger("yap.bench.heavy_hoppers",
                Integer.getInteger("yap.bench.hoppers", 128))
                : Integer.getInteger("yap.bench.hoppers", 256);
        boolean heavy = "heavypop".equals(scenario) || "fullcite".equals(scenario);
        int pilesNeeded = Math.max(1, (entities + MAX_TNT_PER_CHUNK - 1) / MAX_TNT_PER_CHUNK);

        ListJobs jobs = new ListJobs();
        for (int[][] piles : HEAVY_PILES) {
            int use = Math.min(pilesNeeded, piles.length);
            int remaining = entities;
            for (int p = 0; p < use; p++) {
                int[] c = piles[p];
                int n = remaining / (use - p);
                remaining -= n;
                jobs.add(c[0], c[1], n, heavy && p == 0 ? hoppers : 0);
            }
        }

        AtomicInteger left = new AtomicInteger(jobs.size());
        if (jobs.size() == 0) {
            onReady.accept(0);
            return;
        }
        YapSched.global(plugin, () -> {
            for (Job job : jobs.items) {
                world.setChunkForceLoaded(job.cx, job.cz, true);
            }
            for (Job job : jobs.items) {
                YapSched.regionChunk(plugin, world, job.cx, job.cz, () -> {
                    try {
                        world.getChunkAt(job.cx, job.cz).load(true);
                        int bx = (job.cx << 4) + 8;
                        int bz = (job.cz << 4) + 8;
                        int y = Math.max(world.getHighestBlockYAt(bx, bz) + 2, 80);
                        for (int i = 0; i < job.tnt; i++) {
                            TNTPrimed tnt = world.spawn(
                                    new Location(world,
                                            bx + (i % 8) * 0.1,
                                            y + (i / 64) * 0.2,
                                            bz + (i / 8) * 0.1),
                                    TNTPrimed.class);
                            tnt.setFuseTicks(20 * 60 * 10);
                            tnt.setYield(0f);
                            tnt.setIsIncendiary(false);
                        }
                        if (job.hoppers > 0) {
                            int ox = job.cx << 4;
                            int oz = job.cz << 4;
                            int hy = Math.max(world.getHighestBlockYAt(ox + 2, oz + 2), 64);
                            for (int i = 0; i < job.hoppers; i++) {
                                int x = ox + (i % 16);
                                int z = oz + ((i / 16) % 16);
                                int yy = hy + (i / 256);
                                world.getBlockAt(x, yy, z).setType(Material.STONE);
                                world.getBlockAt(x, yy + 1, z).setType(Material.HOPPER);
                            }
                        }
                    } finally {
                        if (left.decrementAndGet() == 0) {
                            int expected = entities * 4;
                            plugin.getLogger().info(scenario + " region-ready — TNT/quad=" + entities
                                    + (heavy ? " hoppers/quad=" + hoppers : "")
                                    + " totalTNT=" + expected);
                            YapSched.global(plugin, () -> onReady.accept(expected));
                        }
                    }
                });
            }
        });
    }

    static void snapshotAsync(JavaPlugin plugin, World world, Consumer<BenchRegionLoad.LoadSnapshot> cb) {
        Map<String, Integer> byType = new ConcurrentHashMap<>();
        AtomicInteger tnt = new AtomicInteger();
        AtomicInteger fuseSum = new AtomicInteger();
        AtomicInteger hoppers = new AtomicInteger();
        AtomicInteger entities = new AtomicInteger();
        AtomicInteger villagers = new AtomicInteger();

        String scenario = System.getProperty("yap.bench.scenario", "");
        forEachChunk(plugin, world, interestChunks(scenario), c -> {
            int cx = (int) c[0];
            int cz = (int) c[1];
            var chunk = world.getChunkAt(cx, cz);
            if (!chunk.isLoaded()) {
                chunk.load(true);
            }
            for (Entity e : chunk.getEntities()) {
                entities.incrementAndGet();
                byType.merge(e.getType().name(), 1, Integer::sum);
                if (e instanceof TNTPrimed tntPrimed) {
                    tnt.incrementAndGet();
                    fuseSum.addAndGet(tntPrimed.getFuseTicks());
                }
                if (e instanceof Villager) {
                    villagers.incrementAndGet();
                }
            }
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Hopper) {
                    hoppers.incrementAndGet();
                }
            }
        }, () -> {
            int t = tnt.get();
            double fuseMean = t == 0 ? 0.0 : (double) fuseSum.get() / t;
            String entityTop = byType.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(12)
                    .map(en -> en.getKey() + "=" + en.getValue())
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            cb.accept(new BenchRegionLoad.LoadSnapshot(
                    t, fuseMean, hoppers.get(), entities.get(),
                    Bukkit.getOnlinePlayers().size(), villagers.get(),
                    world.getLoadedChunks().length, entityTop));
        });
    }

    record Job(int cx, int cz, int tnt, int hoppers) {
    }

    static final class ListJobs {
        final java.util.ArrayList<Job> items = new java.util.ArrayList<>();

        void add(int cx, int cz, int tnt, int hoppers) {
            items.add(new Job(cx, cz, tnt, hoppers));
        }

        int size() {
            return items.size();
        }
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}

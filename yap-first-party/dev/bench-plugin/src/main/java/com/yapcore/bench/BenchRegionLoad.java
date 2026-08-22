package com.yapcore.bench;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.GameRule;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Folia-safe load inject + snapshots. Chunk/world mutation must run on the owning
 * region thread — never from {@code GlobalRegionScheduler}.
 */
final class BenchRegionLoad {

    static final int[][] INTERIOR = {
            {2, 2}, {-3, 2}, {2, -3}, {-3, -3}
    };

    static final int[][][] HEAVY_PILES = {
            {{8, 8}, {12, 12}, {8, 12}, {12, 8}},
            {{-9, 8}, {-13, 12}, {-9, 12}, {-13, 8}},
            {{8, -9}, {12, -13}, {8, -13}, {12, -9}},
            {{-9, -9}, {-13, -13}, {-9, -13}, {-13, -9}},
    };

    private static final int MAX_TNT_PER_CHUNK = 600;

    private BenchRegionLoad() {
    }

    record LoadSnapshot(int tntAlive, double fuseMean, int hoppers, int entitiesTotal,
                        int players, int villagers, int loadedChunks, String entityTop) {
    }

    static final int[][] DEEP_HOME_CHUNKS = spreadHomeChunks();

    private static int[][] spreadHomeChunks() {
        int[][] homes = BenchSpreadGrid.homes();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        java.util.ArrayList<int[]> chunks = new java.util.ArrayList<>();
        for (int[] xz : homes) {
            int cx = xz[0] >> 4;
            int cz = xz[1] >> 4;
            String key = cx + "," + cz;
            if (seen.add(key)) {
                chunks.add(new int[]{cx, cz});
            }
        }
        return chunks.toArray(new int[0][]);
    }

    /** Region-safe highpop/fullcite world fixtures + optional deep TNT (fullcite). */
    static void preparePopBench(JavaPlugin plugin, World world, String scenario,
                                Consumer<Integer> onReady) {
        YapSched.global(plugin, () -> {
            world.setSpawnLocation(0, 80, 0);
            world.setGameRule(GameRule.SPAWN_MONSTERS, false);
        });
        Set<long[]> chunks = popInterestChunks("fullcite".equals(scenario));
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
            }, () -> injectHighpopFixtures(plugin, world, scenario, onReady));
        });
    }

    private static Set<long[]> popInterestChunks(boolean fullcite) {
        Set<long[]> out = new LinkedHashSet<>();
        for (int[] c : INTERIOR) {
            out.add(pack(c[0], c[1]));
        }
        for (int[] c : DEEP_HOME_CHUNKS) {
            out.add(pack(c[0], c[1]));
        }
        for (int[] xz : BenchSpreadGrid.homes()) {
            out.add(pack(xz[0] >> 4, xz[1] >> 4));
        }
        if (fullcite) {
            for (int[][] piles : HEAVY_PILES) {
                for (int[] c : piles) {
                    out.add(pack(c[0], c[1]));
                }
            }
        }
        return out;
    }

    private static void injectHighpopFixtures(JavaPlugin plugin, World world, String scenario,
                                              Consumer<Integer> onReady) {
        int hopperCount = Integer.getInteger("yap.bench.hoppers", 64);
        int villagers = Integer.getInteger("yap.bench.villagers", 32);
        int animals = Integer.getInteger("yap.bench.animals", 48);
        EntityType[] animalTypes = {EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN};

        Set<long[]> interior = new LinkedHashSet<>();
        for (int[] c : INTERIOR) {
            interior.add(pack(c[0], c[1]));
        }
        Set<long[]> deep = new LinkedHashSet<>();
        for (int[] c : DEEP_HOME_CHUNKS) {
            deep.add(pack(c[0], c[1]));
        }

        forEachChunk(plugin, world, interior, c -> {
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
            int hy = Math.max(world.getHighestBlockYAt(bx + 2, bz + 2), 64);
            for (int i = 0; i < hopperCount; i++) {
                int x = bx + (i % 16);
                int z = bz + ((i / 16) % 16);
                int yy = hy + (i / 256);
                world.getBlockAt(x, yy, z).setType(Material.STONE);
                world.getBlockAt(x, yy + 1, z).setType(Material.HOPPER);
            }
            int cxOff = bx + 4;
            int czOff = bz + 4;
            int y = Math.max(world.getHighestBlockYAt(cxOff, czOff), 64) + 1;
            world.getBlockAt(cxOff, y, czOff).setType(Material.CHEST);
            world.getBlockAt(cxOff + 1, y, czOff).setType(Material.CHEST);
            for (int i = 0; i < villagers / 4; i++) {
                world.spawnEntity(
                        new Location(world, cxOff + 0.5, y, czOff + 2.5 + i * 0.3), EntityType.VILLAGER);
            }
            int ax = bx + 10;
            int az = bz + 10;
            int ay = Math.max(world.getHighestBlockYAt(ax, az), 64) + 1;
            for (int i = 0; i < animals / 4; i++) {
                Entity e = world.spawnEntity(new Location(world, ax + (i % 4), ay, az + (i / 4)),
                        animalTypes[i % animalTypes.length]);
                if (e instanceof Animals a) {
                    a.setAdult();
                    a.setAgeLock(true);
                    a.setBreed(false);
                }
            }
            int rx = bx + 1;
            int rz = bz + 1;
            int ry = Math.max(world.getHighestBlockYAt(rx, rz), 64) + 2;
            world.getBlockAt(rx, ry, rz).setType(Material.OBSERVER);
            world.getBlockAt(rx, ry + 1, rz).setType(Material.OBSERVER);
            world.getBlockAt(rx + 1, ry, rz).setType(Material.REDSTONE_LAMP);
            world.getBlockAt(rx + 1, ry + 1, rz).setType(Material.REDSTONE_BLOCK);
        }, () -> forEachChunk(plugin, world, deep, c -> {
            int cx = (int) c[0];
            int cz = (int) c[1];
            int bx = (cx << 4) + 8;
            int bz = (cz << 4) + 8;
            int y = Math.max(world.getHighestBlockYAt(bx, bz), 64) + 1;
            world.getBlockAt(bx, y, bz).setType(Material.CHEST);
        }, () -> forEachChunk(plugin, world, deepSpreadChunks(), c -> {
            int cx = (int) c[0];
            int cz = (int) c[1];
            for (int[] xz : BenchSpreadGrid.homes()) {
                if ((xz[0] >> 4) != cx || (xz[1] >> 4) != cz) {
                    continue;
                }
                int bx = xz[0];
                int bz = xz[1];
                int y = Math.max(world.getHighestBlockYAt(bx, bz), 64) + 2;
                world.getBlockAt(bx, y, bz).setType(Material.OBSERVER);
                world.getBlockAt(bx, y + 1, bz).setType(Material.OBSERVER);
                world.getBlockAt(bx + 1, y, bz).setType(Material.REDSTONE_LAMP);
                world.getBlockAt(bx + 1, y + 1, bz).setType(Material.REDSTONE_BLOCK);
                world.getBlockAt(bx - 1, y, bz).setType(Material.STONE);
                world.getBlockAt(bx - 1, y + 1, bz).setType(Material.HOPPER);
                world.getBlockAt(bx, y, bz + 1).setType(Material.CHEST);
            }
        }, () -> {
            plugin.getLogger().info("highpop world fixtures ready (region) — scenario=" + scenario);
            if ("fullcite".equals(scenario)) {
                injectTntAndHoppers(plugin, world, scenario, onReady);
            } else {
                YapSched.global(plugin, () -> onReady.accept(0));
            }
        }));
    }

    static Set<long[]> interestChunks() {
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

    private static long[] pack(int cx, int cz) {
        return new long[]{cx, cz};
    }

    private static Set<long[]> deepSpreadChunks() {
        Set<long[]> out = new LinkedHashSet<>();
        for (int[] xz : BenchSpreadGrid.homes()) {
            out.add(pack(xz[0] >> 4, xz[1] >> 4));
        }
        return out;
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
        Set<long[]> chunks = interestChunks();
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

    /** Region-safe prepare for idle|entity|farm|heavypop. {@code onReady} gets expected TNT. */
    static void prepare(JavaPlugin plugin, World world, String scenario, Consumer<Integer> onReady) {
        clearInterest(plugin, world, () -> {
            switch (scenario) {
                case "entity", "heavypop" -> injectTntAndHoppers(plugin, world, scenario, onReady);
                case "farm" -> injectFarm(plugin, world, () -> onReady.accept(0));
                default -> {
                    plugin.getLogger().info("Idle scenario — no load injected (regression guard only)");
                    onReady.accept(0);
                }
            }
        });
    }

    private static void injectFarm(JavaPlugin plugin, World world, Runnable onDone) {
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

    private static void injectTntAndHoppers(JavaPlugin plugin, World world, String scenario,
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

    static void snapshotAsync(JavaPlugin plugin, World world, Consumer<LoadSnapshot> cb) {
        Map<String, Integer> byType = new ConcurrentHashMap<>();
        AtomicInteger tnt = new AtomicInteger();
        AtomicInteger fuseSum = new AtomicInteger();
        AtomicInteger hoppers = new AtomicInteger();
        AtomicInteger entities = new AtomicInteger();
        AtomicInteger villagers = new AtomicInteger();

        forEachChunk(plugin, world, interestChunks(), c -> {
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
            cb.accept(new LoadSnapshot(
                    t, fuseMean, hoppers.get(), entities.get(),
                    Bukkit.getOnlinePlayers().size(), villagers.get(),
                    world.getLoadedChunks().length, entityTop));
        });
    }

    private record Job(int cx, int cz, int tnt, int hoppers) {
    }

    private static final class ListJobs {
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

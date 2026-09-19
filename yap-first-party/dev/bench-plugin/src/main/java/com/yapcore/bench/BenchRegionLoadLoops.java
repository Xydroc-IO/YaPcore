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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Folia-safe chunk iteration, clear, inject loops, and load snapshots. */
final class BenchRegionLoadLoops {

    private BenchRegionLoadLoops() {
    }

    static final int[][] INTERIOR = {
            {2, 2}, {-12, 2}, {2, -3}, {-12, -3}
    };

    static final int[][][] HEAVY_PILES = {
            {{8, 8}, {12, 12}, {8, 12}, {12, 8}},
            {{-9, 8}, {-13, 12}, {-9, 12}, {-13, 8}},
            {{8, -9}, {12, -13}, {8, -13}, {12, -9}},
            {{-9, -9}, {-13, -13}, {-9, -13}, {-13, -9}},
    };

    static final int MAX_TNT_PER_CHUNK = 600;

    /** Villagers the bench planted on the four interior chunks. Sample-start classifies each. */
    record PlantedVillager(UUID id, int cx, int cz) {
    }

    static final List<PlantedVillager> PLANTED_VILLAGERS = new CopyOnWriteArrayList<>();

    static void clearPlantedVillagers() {
        PLANTED_VILLAGERS.clear();
    }

    static boolean interiorChunk(int cx, int cz) {
        for (int[] c : INTERIOR) {
            if (c[0] == cx && c[1] == cz) {
                return true;
            }
        }
        return false;
    }

    static void logVillagersPlaced(JavaPlugin plugin, int cx, int cz, List<Entity> spawned) {
        StringBuilder line = new StringBuilder();
        line.append("villagers placed chunk=").append(cx).append(',').append(cz)
                .append(" n=").append(spawned.size());
        for (Entity e : spawned) {
            PLANTED_VILLAGERS.add(new PlantedVillager(e.getUniqueId(), cx, cz));
            var loc = e.getLocation();
            line.append(' ').append(e.getUniqueId())
                    .append('@').append(String.format(Locale.ROOT, "%.1f,%.1f,%.1f", loc.getX(), loc.getY(), loc.getZ()));
        }
        plugin.getLogger().info(line.toString());
    }

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

    /** Keep chunk loaded without GlobalRegionScheduler (aligned soft-wave can stall global). */
    static void pinChunk(JavaPlugin plugin, World world, int cx, int cz) {
        try {
            world.addPluginChunkTicket(cx, cz, plugin);
        } catch (Throwable ignored) {
            // Older API — best-effort load only.
        }
    }

    static void tryForceLoad(World world, int cx, int cz) {
        try {
            world.setChunkForceLoaded(cx, cz, true);
        } catch (IllegalStateException ignored) {
            // Not on global tick thread — plugin ticket is enough for bench fixtures.
        }
    }

    static void forEachChunk(JavaPlugin plugin, World world, Set<long[]> chunks,
                             Consumer<long[]> perChunk, Runnable onDone) {
        forEachChunk(plugin, world, chunks, perChunk, onDone, false);
    }

    static void forEachChunk(JavaPlugin plugin, World world, Set<long[]> chunks,
                             Consumer<long[]> perChunk, Runnable onDone, boolean forceRegion) {
        if (chunks.isEmpty()) {
            onDone.run();
            return;
        }
        // Sync getEntities from the (0,0) thread after a cut only sees one shard.
        if (!forceRegion) {
            try {
                for (long[] c : chunks) {
                    perChunk.accept(c);
                }
                onDone.run();
                return;
            } catch (IllegalStateException | UnsupportedOperationException syncFail) {
                plugin.getLogger().info("forEachChunk sync unavailable — region fan-out (" + syncFail.getMessage() + ")");
            }
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
                        YapSched.region(plugin, world, 0, 0, onDone);
                    }
                }
            });
        }
    }

    static void clearInterest(JavaPlugin plugin, World world, Runnable onDone) {
        clearInterest(plugin, world, interestChunks(), onDone);
    }

    static void clearInterest(JavaPlugin plugin, World world, Set<long[]> chunks, Runnable onDone) {
        // Prefer region path: global can soft-wave stall under aligned-microticks.
        forEachChunk(plugin, world, chunks, c -> {
            int cx = (int) c[0];
            int cz = (int) c[1];
            pinChunk(plugin, world, cx, cz);
            tryForceLoad(world, cx, cz);
            world.getChunkAt(cx, cz).load(true);
            for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
                if (!(e instanceof Player)) {
                    e.remove();
                }
            }
        }, onDone);
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
        Runnable finish = () -> {
            int expected = entities * 4;
            plugin.getLogger().info(scenario + " region-ready — TNT/quad=" + entities
                    + (heavy ? " hoppers/quad=" + hoppers : "")
                    + " totalTNT=" + expected);
            onReady.accept(expected);
        };
        java.util.function.Consumer<Job> runJob = job -> {
            pinChunk(plugin, world, job.cx, job.cz);
            tryForceLoad(world, job.cx, job.cz);
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
        };
        try {
            for (Job job : jobs.items) {
                runJob.accept(job);
            }
            finish.run();
            return;
        } catch (IllegalStateException | UnsupportedOperationException syncFail) {
            plugin.getLogger().info("injectTnt sync unavailable — region fan-out (" + syncFail.getMessage() + ")");
        }
        for (Job job : jobs.items) {
            YapSched.regionChunk(plugin, world, job.cx, job.cz, () -> {
                try {
                    runJob.accept(job);
                } finally {
                    if (left.decrementAndGet() == 0) {
                        YapSched.region(plugin, world, 0, 0, finish);
                    }
                }
            });
        }
    }

    static void snapshotAsync(JavaPlugin plugin, World world, Consumer<BenchRegionLoad.LoadSnapshot> cb) {
        Map<String, Integer> byType = new ConcurrentHashMap<>();
        AtomicInteger tnt = new AtomicInteger();
        AtomicInteger fuseSum = new AtomicInteger();
        AtomicInteger hoppers = new AtomicInteger();
        AtomicInteger entities = new AtomicInteger();
        AtomicInteger villagers = new AtomicInteger();
        Map<String, int[]> pileFuse = new ConcurrentHashMap<>();
        Map<UUID, String> seenVillager = new ConcurrentHashMap<>();
        Map<String, String> interiorLine = new ConcurrentHashMap<>();

        String scenario = System.getProperty("yap.bench.scenario", "");
        boolean regionFanout = YapSched.isRegionized();
        forEachChunk(plugin, world, interestChunks(scenario), c -> {
            int cx = (int) c[0];
            int cz = (int) c[1];
            boolean interior = interiorChunk(cx, cz);
            if (!world.isChunkLoaded(cx, cz)) {
                if (interior) {
                    interiorLine.put(cx + "," + cz, "chunk=" + cx + "," + cz + " loaded=false n=0");
                }
                return;
            }
            var chunk = world.getChunkAt(cx, cz, false);
            if (!chunk.isLoaded()) {
                if (interior) {
                    interiorLine.put(cx + "," + cz, "chunk=" + cx + "," + cz + " loaded=false n=0");
                }
                return;
            }
            StringBuilder onChunk = interior ? new StringBuilder() : null;
            int onChunkN = 0;
            for (Entity e : chunk.getEntities()) {
                entities.incrementAndGet();
                byType.merge(e.getType().name(), 1, Integer::sum);
                if (e instanceof TNTPrimed tntPrimed) {
                    int fuse = tntPrimed.getFuseTicks();
                    tnt.incrementAndGet();
                    fuseSum.addAndGet(fuse);
                    pileFuse.compute(cx + "," + cz, (k, acc) -> {
                        if (acc == null) {
                            acc = new int[2];
                        }
                        acc[0]++;
                        acc[1] += fuse;
                        return acc;
                    });
                }
                if (e instanceof Villager) {
                    villagers.incrementAndGet();
                    var loc = e.getLocation();
                    String where = cx + "," + cz + "@"
                            + String.format(Locale.ROOT, "%.1f,%.1f,%.1f", loc.getX(), loc.getY(), loc.getZ())
                            + (e.isDead() ? " dead" : "");
                    seenVillager.put(e.getUniqueId(), where);
                    if (onChunk != null) {
                        onChunkN++;
                        onChunk.append(' ').append(e.getUniqueId())
                                .append('@')
                                .append(String.format(Locale.ROOT, "%.1f,%.1f,%.1f", loc.getX(), loc.getY(), loc.getZ()));
                    }
                }
            }
            if (interior) {
                interiorLine.put(cx + "," + cz, "chunk=" + cx + "," + cz + " loaded=true n=" + onChunkN + onChunk);
            }
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Hopper) {
                    hoppers.incrementAndGet();
                }
            }
        }, () -> {
            int t = tnt.get();
            double fuseMean = t == 0 ? 0.0 : (double) fuseSum.get() / t;
            if (!pileFuse.isEmpty()) {
                String piles = pileFuse.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(en -> {
                            int[] acc = en.getValue();
                            double mean = acc[0] == 0 ? 0.0 : (double) acc[1] / acc[0];
                            return en.getKey() + "=" + acc[0] + "@" + (int) mean;
                        })
                        .reduce((a, b) -> a + " " + b)
                        .orElse("");
                plugin.getLogger().info("fuse piles " + piles + " mean=" + fmt(fuseMean));
            }
            int living = logVillagerTrace(plugin, world, interiorLine, seenVillager);
            int reported = PLANTED_VILLAGERS.isEmpty() ? villagers.get() : living;
            String entityTop = byType.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(12)
                    .map(en -> en.getKey() + "=" + en.getValue())
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            cb.accept(new BenchRegionLoad.LoadSnapshot(
                    t, fuseMean, hoppers.get(), entities.get(),
                    Bukkit.getOnlinePlayers().size(), reported,
                    world.getLoadedChunks().length, entityTop));
        }, regionFanout);
    }

    /**
     * Each planted villager is still on its chunk, alive somewhere else, or gone.
     * The sample count is planted villagers who are still alive, not whoever stayed on the four chunks.
     */
    static int logVillagerTrace(JavaPlugin plugin, World world,
                                Map<String, String> interiorLine, Map<UUID, String> seenVillager) {
        for (int[] c : INTERIOR) {
            String key = c[0] + "," + c[1];
            plugin.getLogger().info("villagers sample-start "
                    + interiorLine.getOrDefault(key, "chunk=" + key + " loaded=false n=0"));
        }
        int still = 0;
        int moved = 0;
        int removed = 0;
        List<String> lines = new ArrayList<>();
        for (PlantedVillager planted : PLANTED_VILLAGERS) {
            String origin = planted.cx() + "," + planted.cz();
            String seen = seenVillager.get(planted.id());
            String disposition;
            if (seen != null && seen.endsWith(" dead")) {
                disposition = "removed " + seen;
                removed++;
            } else if (seen != null && seen.startsWith(origin + "@")) {
                disposition = "still " + seen;
                still++;
            } else if (seen != null) {
                disposition = "moved " + seen;
                moved++;
            } else {
                disposition = dispositionOffChunk(world, planted.id());
                if (disposition.startsWith("moved")) {
                    moved++;
                } else {
                    removed++;
                }
            }
            lines.add("villagers trace " + planted.id() + " from=" + origin + " " + disposition);
        }
        int alive = still + moved;
        plugin.getLogger().info("villagers sample-start planted=" + PLANTED_VILLAGERS.size()
                + " alive=" + alive
                + " still=" + still + " moved=" + moved + " removed=" + removed);
        for (String line : lines) {
            plugin.getLogger().info(line);
        }
        return alive;
    }

    static String dispositionOffChunk(World world, UUID id) {
        try {
            Entity e = world.getEntity(id);
            if (e == null || e.isDead() || !e.isValid()) {
                return "removed";
            }
            var loc = e.getLocation();
            int cx = loc.getBlockX() >> 4;
            int cz = loc.getBlockZ() >> 4;
            return "moved " + cx + "," + cz + "@"
                    + String.format(Locale.ROOT, "%.1f,%.1f,%.1f", loc.getX(), loc.getY(), loc.getZ())
                    + (e.isDead() ? " dead" : "");
        } catch (Throwable failed) {
            return "removed lookup=" + failed.getClass().getSimpleName();
        }
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

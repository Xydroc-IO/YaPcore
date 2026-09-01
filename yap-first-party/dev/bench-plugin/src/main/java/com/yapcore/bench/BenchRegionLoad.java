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
        })));
    }

    static Set<long[]> interestChunks() {
        return interestChunks(System.getProperty("yap.bench.scenario", ""));
    }

    static Set<long[]> interestChunks(String scenario) {
        if ("spawncollapse".equals(scenario)) {
            return spawnCollapseChunks();
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

    /**
     * Spawn-collapse interest: a single Folia region around spawn (chunks in one
     * contiguous 3×3 block) so all load shares one tick runner.
     * <p>With {@code -Dyap.bench.lobes=2}, load is split into west/east lobes separated by a
     * Folia-safe gap so regions can tick in parallel (see Folia empty-section radius).
     */
    static final int[][] SPAWN_COLLAPSE_CHUNKS = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /** Half-lobe 3×3 offsets; applied at ±{@code lobeOffsetChunks}. */
    static final int[][] LOBE_OFFSETS = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /** After spawn, pin lobes only (corridor unforced) — citeable dynamic carve vs contiguous stock. */
    static boolean stripTwoPhase() {
        return Boolean.parseBoolean(System.getProperty("yap.bench.strip_two_phase", "false"));
    }

    /** YaP-only: full contiguous force-load (no lobe gap) for dynamic carve+partition cite. */
    static boolean contiguousCarve() {
        return Boolean.parseBoolean(System.getProperty("yap.bench.contiguous_carve", "false"));
    }

    static int stripHalfWidth() {
        return Integer.getInteger("yap.bench.strip_half_width", 0);
    }

    static int stripZRadius() {
        return Math.max(0, Integer.getInteger("yap.bench.strip_z_radius", 1));
    }

    /** Gap half-width in chunks; lobes are chunks with {@code |cx| > gapHalf}. */
    static int stripGapHalf() {
        int gap = Math.max(0, Integer.getInteger("yap.bench.strip_gap_half", 0));
        if (gap > 0) {
            return gap;
        }
        int half = stripHalfWidth();
        if (half > 0 && stripTwoPhase()) {
            // Folia-safe default: ~25% of strip center empty (matches gap1-style cite).
            return Math.max(16, half / 3);
        }
        return 0;
    }

    /** Full contiguous strip — used to spawn load before corridor unpin (two-phase). */
    static Set<long[]> spawnCollapseFullStripChunks() {
        int stripHalf = stripHalfWidth();
        if (stripHalf <= 0) {
            return Set.of();
        }
        int zRadius = stripZRadius();
        Set<long[]> out = new LinkedHashSet<>();
        for (int cx = -stripHalf; cx <= stripHalf; cx++) {
            for (int cz = -zRadius; cz <= zRadius; cz++) {
                out.add(pack(cx, cz));
            }
        }
        return out;
    }

    /** Lobe pin set: wide strip minus Folia gap corridor. */
    static Set<long[]> spawnCollapseLobePinChunks() {
        int stripHalf = stripHalfWidth();
        if (stripHalf <= 0) {
            return Set.of();
        }
        int zRadius = stripZRadius();
        int gapHalf = stripGapHalf();
        Set<long[]> out = new LinkedHashSet<>();
        for (int cx = -stripHalf; cx <= stripHalf; cx++) {
            if (gapHalf > 0 && Math.abs(cx) <= gapHalf) {
                continue;
            }
            for (int cz = -zRadius; cz <= zRadius; cz++) {
                out.add(pack(cx, cz));
            }
        }
        return out;
    }

    static Set<long[]> spawnCollapseChunks() {
        int lobes = Math.max(1, Integer.getInteger("yap.bench.lobes", 1));
        // Contiguous wide strip so corridor carve can unload a Folia-safe middle gap.
        int stripHalf = stripHalfWidth();
        if (stripHalf > 0) {
            if (contiguousCarve()) {
                return spawnCollapseFullStripChunks();
            }
            if (stripTwoPhase()) {
                return spawnCollapseLobePinChunks();
            }
            int zRadius = stripZRadius();
            // Optional pre-carved Folia-safe gap (chunks with |cx| <= gapHalf are not force-loaded).
            int gapHalf = stripGapHalf();
            Set<long[]> out = new LinkedHashSet<>();
            for (int cx = -stripHalf; cx <= stripHalf; cx++) {
                if (gapHalf > 0 && Math.abs(cx) <= gapHalf) {
                    continue;
                }
                for (int cz = -zRadius; cz <= zRadius; cz++) {
                    out.add(pack(cx, cz));
                }
            }
            return out;
        }
        if (lobes < 2) {
            Set<long[]> out = new LinkedHashSet<>();
            for (int[] c : SPAWN_COLLAPSE_CHUNKS) {
                out.add(pack(c[0], c[1]));
            }
            return out;
        }
        // Gap must exceed Folia's adjacency (≈2×emptySectionCreateRadius sections).
        // Default offset 40 chunks → ~20-chunk empty corridor at x≈0 — safe at grid-exponent 0–2.
        int offset = Math.max(16, Integer.getInteger("yap.bench.lobe_offset_chunks", 40));
        Set<long[]> out = new LinkedHashSet<>();
        for (int sign : new int[]{-1, 1}) {
            int ox = sign * offset;
            for (int[] c : LOBE_OFFSETS) {
                out.add(pack(c[0] + ox, c[1]));
            }
        }
        return out;
    }

    /** Region-safe prepare for idle|entity|farm|heavypop|spawncollapse. {@code onReady} gets expected TNT. */
    static void prepare(JavaPlugin plugin, World world, String scenario, Consumer<Integer> onReady) {
        if ("spawncollapse".equals(scenario)) {
            prepareSpawnCollapse(plugin, world, onReady);
            return;
        }
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

    /**
     * Dense primed TNT + hoppers + hostile/passive mobs inside one region so stock
     * Folia shows TPS collapse; gates entity-offload / hot-region work (Phase 3.2).
     */
    static void prepareSpawnCollapse(JavaPlugin plugin, World world, Consumer<Integer> onReady) {
        int entities = Integer.getInteger("yap.bench.entities", 800);
        int hoppers = Integer.getInteger("yap.bench.hoppers", 256);
        int mobs = Integer.getInteger("yap.bench.mobs", 200);
        final boolean twoPhase = stripTwoPhase() && stripHalfWidth() > 0 && !contiguousCarve();
        // YaP two-phase: spawn on lobe pin set only (same totals as stock contiguous strip).
        // contiguous_carve: full strip for dynamic carve cite (stock vs YaP both contiguous).
        final Set<long[]> spawnChunks = twoPhase ? spawnCollapseLobePinChunks() : spawnCollapseChunks();
        final Set<long[]> pinChunks = twoPhase ? spawnChunks : spawnChunks;
        YapSched.global(plugin, () -> {
            world.setSpawnLocation(0, 80, 0);
            world.setGameRule(GameRule.SPAWN_MONSTERS, false);
            for (long[] c : spawnChunks) {
                world.setChunkForceLoaded((int) c[0], (int) c[1], true);
            }
            forEachChunk(plugin, world, spawnChunks, c -> {
                int cx = (int) c[0];
                int cz = (int) c[1];
                world.getChunkAt(cx, cz).load(true);
                for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
                    if (!(e instanceof Player)) {
                        e.remove();
                    }
                }
            }, () -> injectSpawnCollapseLoad(plugin, world, spawnChunks, pinChunks, twoPhase,
                    entities, hoppers, mobs, onReady));
        });
    }

    /** Unpin corridor after spawn so sim tickets do not bridge partitioned shards. */
    private static void applyLobePinOnly(
            JavaPlugin plugin,
            World world,
            Set<long[]> spawnChunks,
            Set<long[]> pinChunks,
            int expectedTnt,
            Consumer<Integer> onReady) {
        final java.util.ArrayList<long[]> corridor = new java.util.ArrayList<>();
        final java.util.ArrayList<long[]> lobes = new java.util.ArrayList<>(pinChunks);
        for (long[] c : spawnChunks) {
            boolean keep = false;
            for (long[] p : pinChunks) {
                if (p[0] == c[0] && p[1] == c[1]) {
                    keep = true;
                    break;
                }
            }
            if (!keep) {
                corridor.add(c);
            }
        }
        if (corridor.isEmpty() || lobes.isEmpty()) {
            YapSched.global(plugin, () -> onReady.accept(expectedTnt));
            return;
        }
        // Move corridor load onto lobes before unpin — otherwise entities despawn / evade snapshot.
        forEachChunk(plugin, world, new LinkedHashSet<>(corridor), c -> {
            int cx = (int) c[0];
            int cz = (int) c[1];
            long[] dest = lobes.get(Math.floorMod(cx + cz, lobes.size()));
            int dcx = (int) dest[0];
            int dcz = (int) dest[1];
            double x = (dcx << 4) + 8.0;
            double z = (dcz << 4) + 8.0;
            for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                try {
                    double y = Math.max(entity.getLocation().getY(),
                            world.getHighestBlockYAt((int) x, (int) z) + 1.0);
                    entity.teleport(new Location(world, x, y, z));
                } catch (Throwable ignored) {
                }
            }
        }, () -> YapSched.global(plugin, () -> {
            int unpinned = 0;
            for (long[] c : spawnChunks) {
                int cx = (int) c[0];
                int cz = (int) c[1];
                boolean keep = false;
                for (long[] p : pinChunks) {
                    if (p[0] == cx && p[1] == cz) {
                        keep = true;
                        break;
                    }
                }
                if (!keep) {
                    world.setChunkForceLoaded(cx, cz, false);
                    unpinned++;
                }
            }
            plugin.getLogger().info("spawncollapse two-phase — spawned on " + spawnChunks.size()
                    + " chunks, relocated corridor→lobes, pinned " + pinChunks.size()
                    + " lobe chunks, unpinned corridor=" + unpinned + " gapHalf=" + stripGapHalf());
            onReady.accept(expectedTnt);
        }));
    }

    private static void injectSpawnCollapseLoad(
            JavaPlugin plugin,
            World world,
            Set<long[]> chunks,
            Set<long[]> pinChunks,
            boolean twoPhase,
            int totalTnt,
            int totalHoppers,
            int totalMobs,
            Consumer<Integer> onReady) {
        List<long[]> list = new java.util.ArrayList<>(chunks);
        int n = Math.max(1, list.size());
        AtomicInteger left = new AtomicInteger(n);
        AtomicInteger spawnedTnt = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            long[] c = list.get(i);
            int cx = (int) c[0];
            int cz = (int) c[1];
            int tntHere = totalTnt / n + (i < totalTnt % n ? 1 : 0);
            int hopHere = totalHoppers / n + (i < totalHoppers % n ? 1 : 0);
            int mobHere = totalMobs / n + (i < totalMobs % n ? 1 : 0);
            YapSched.regionChunk(plugin, world, cx, cz, () -> {
                try {
                    int bx = (cx << 4) + 8;
                    int bz = (cz << 4) + 8;
                    int y = Math.max(world.getHighestBlockYAt(bx, bz) + 2, 80);
                    for (int t = 0; t < tntHere; t++) {
                        TNTPrimed tnt = world.spawn(
                                new Location(world,
                                        bx + (t % 8) * 0.1,
                                        y + (t / 64) * 0.2,
                                        bz + (t / 8) * 0.1),
                                TNTPrimed.class);
                        tnt.setFuseTicks(20 * 60 * 10);
                        tnt.setYield(0f);
                        tnt.setIsIncendiary(false);
                        spawnedTnt.incrementAndGet();
                    }
                    int ox = cx << 4;
                    int oz = cz << 4;
                    int hy = Math.max(world.getHighestBlockYAt(ox + 2, oz + 2), 64);
                    for (int h = 0; h < hopHere; h++) {
                        int x = ox + (h % 16);
                        int z = oz + ((h / 16) % 16);
                        int yy = hy + (h / 256);
                        world.getBlockAt(x, yy, z).setType(Material.STONE);
                        world.getBlockAt(x, yy + 1, z).setType(Material.HOPPER);
                    }
                    EntityType[] types = {
                            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
                            EntityType.COW, EntityType.SHEEP, EntityType.PIG
                    };
                    for (int m = 0; m < mobHere; m++) {
                        org.bukkit.entity.Entity spawned = world.spawnEntity(
                                new Location(world, bx + (m % 4) * 0.5, y, bz + (m / 4) * 0.5),
                                types[m % types.length]);
                        if (spawned instanceof org.bukkit.entity.LivingEntity living) {
                            living.setRemoveWhenFarAway(false);
                            living.setPersistent(true);
                        }
                    }
                } finally {
                    if (left.decrementAndGet() == 0) {
                        int expected = spawnedTnt.get();
                        String layout = stripHalfWidth() > 0
                                ? (twoPhase ? " (two-phase lobe-spawn carve-capable)"
                                : " (wide-strip carve-capable)")
                                : Integer.getInteger("yap.bench.lobes", 1) >= 2
                                ? " (dual-lobe parallel-capable)"
                                : " (single-region overload)";
                        plugin.getLogger().info("spawncollapse region-ready — TNT=" + expected
                                + " hoppers≈" + totalHoppers + " mobs≈" + totalMobs
                                + " chunks=" + n + layout);
                        YapSched.global(plugin, () -> onReady.accept(expected));
                    }
                }
            });
        }
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

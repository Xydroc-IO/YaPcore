package com.yapcore.bench;

import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.GameRule;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Scenario setup / prepare for region load benches. */
final class BenchRegionLoadSetup {

    private BenchRegionLoadSetup() {
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

    private static Set<long[]> deepSpreadChunks() {
        Set<long[]> out = new LinkedHashSet<>();
        for (int[] xz : BenchSpreadGrid.homes()) {
            out.add(BenchRegionLoadLoops.pack(xz[0] >> 4, xz[1] >> 4));
        }
        return out;
    }

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
            BenchRegionLoadLoops.forEachChunk(plugin, world, chunks, c -> {
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
        for (int[] c : BenchRegionLoadLoops.INTERIOR) {
            out.add(BenchRegionLoadLoops.pack(c[0], c[1]));
        }
        for (int[] c : DEEP_HOME_CHUNKS) {
            out.add(BenchRegionLoadLoops.pack(c[0], c[1]));
        }
        for (int[] xz : BenchSpreadGrid.homes()) {
            out.add(BenchRegionLoadLoops.pack(xz[0] >> 4, xz[1] >> 4));
        }
        if (fullcite) {
            for (int[][] piles : BenchRegionLoadLoops.HEAVY_PILES) {
                for (int[] c : piles) {
                    out.add(BenchRegionLoadLoops.pack(c[0], c[1]));
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
        for (int[] c : BenchRegionLoadLoops.INTERIOR) {
            interior.add(BenchRegionLoadLoops.pack(c[0], c[1]));
        }
        Set<long[]> deep = new LinkedHashSet<>();
        for (int[] c : DEEP_HOME_CHUNKS) {
            deep.add(BenchRegionLoadLoops.pack(c[0], c[1]));
        }

        BenchRegionLoadLoops.forEachChunk(plugin, world, interior, c -> {
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
        }, () -> BenchRegionLoadLoops.forEachChunk(plugin, world, deep, c -> {
            int cx = (int) c[0];
            int cz = (int) c[1];
            int bx = (cx << 4) + 8;
            int bz = (cz << 4) + 8;
            int y = Math.max(world.getHighestBlockYAt(bx, bz), 64) + 1;
            world.getBlockAt(bx, y, bz).setType(Material.CHEST);
        }, () -> BenchRegionLoadLoops.forEachChunk(plugin, world, deepSpreadChunks(), c -> {
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
                BenchRegionLoadLoops.injectTntAndHoppers(plugin, world, scenario, onReady);
            } else {
                YapSched.global(plugin, () -> onReady.accept(0));
            }
        })));
    }

    /** Region-safe prepare for idle|entity|farm|heavypop|spawncollapse. {@code onReady} gets expected TNT. */
    static void prepare(JavaPlugin plugin, World world, String scenario, Consumer<Integer> onReady) {
        if ("spawncollapse".equals(scenario)) {
            prepareSpawnCollapse(plugin, world, onReady);
            return;
        }
        BenchRegionLoadLoops.clearInterest(plugin, world, () -> {
            switch (scenario) {
                case "entity", "heavypop" -> BenchRegionLoadLoops.injectTntAndHoppers(plugin, world, scenario, onReady);
                case "farm" -> BenchRegionLoadLoops.injectFarm(plugin, world, () -> onReady.accept(0));
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
        final boolean twoPhase = BenchRegionSpawnChunks.stripTwoPhase() && BenchRegionSpawnChunks.stripHalfWidth() > 0 && !BenchRegionSpawnChunks.contiguousCarve();
        // YaP two-phase: spawn on lobe pin set only (same totals as stock contiguous strip).
        // contiguous_carve: full strip for dynamic carve cite (stock vs YaP both contiguous).
        final Set<long[]> spawnChunks = twoPhase ? BenchRegionSpawnChunks.spawnCollapseLobePinChunks() : BenchRegionSpawnChunks.spawnCollapseChunks();
        final Set<long[]> pinChunks = twoPhase ? spawnChunks : spawnChunks;
        YapSched.global(plugin, () -> {
            world.setSpawnLocation(0, 80, 0);
            world.setGameRule(GameRule.SPAWN_MONSTERS, false);
            for (long[] c : spawnChunks) {
                world.setChunkForceLoaded((int) c[0], (int) c[1], true);
            }
            BenchRegionLoadLoops.forEachChunk(plugin, world, spawnChunks, c -> {
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
        BenchRegionLoadLoops.forEachChunk(plugin, world, new LinkedHashSet<>(corridor), c -> {
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
                    + " lobe chunks, unpinned corridor=" + unpinned + " gapHalf=" + BenchRegionSpawnChunks.stripGapHalf());
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
                        String layout = BenchRegionSpawnChunks.stripHalfWidth() > 0
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
}

package com.yapcore.bench;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MSPT scoreboard harness.
 * Scenarios: idle | entity | farm | heavypop | highpop | fullcite
 * <p>
 * {@code highpop} = bots + light world fixtures + plugin tax.
 * {@code fullcite} = bots + heavypop-class TNT/hoppers/redstone in deep interior
 * + heavy plugin surface — product cite vs Leaf/Paper under combined pressure.
 */
public final class BenchMsptPlugin extends JavaPlugin implements Listener {

    /** Near-spawn interiors for highpop fixtures (within view-distance). */
    private static final int[][] INTERIOR = {
            {2, 2}, {-3, 2}, {2, -3}, {-3, -3}
    };

    /**
     * Deep bot homes (block xz) — one per spatial quadrant, |xz|≥64 so chunk≥4.
     * Matches swarm.js QUAD_WAYPOINTS; keeps bots off origin-border planes while
     * still exercising all four MT owners (not SE-only).
     */
    private static final int[][] BOT_HOME_XZ = {
            {72, 72}, {-72, 72}, {72, -72}, {-72, -72}
    };

    /**
     * Heavypop TNT/hopper piles — deep interior, first pile is the classic winning
     * mid-load geometry. Cap ~600 primed TNT/chunk before fuse proofs fail on stock;
     * extra piles stay inside the same quadrant (no border-adjacent 3×3 halo).
     */
    private static final int[][][] HEAVY_PILES = {
            {{8, 8}, {12, 12}, {8, 12}, {12, 8}},
            {{-9, 8}, {-13, 12}, {-9, 12}, {-13, 8}},
            {{8, -9}, {12, -13}, {8, -13}, {12, -9}},
            {{-9, -9}, {-13, -13}, {-9, -13}, {-13, -9}},
    };
    private static final int MAX_TNT_PER_CHUNK = 600;

    /** Chunk coords of deep bot homes — force-load + inventory targets (not origin-border). */
    private static final int[][] DEEP_HOME_CHUNKS = {
            {4, 4}, {-5, 4}, {4, -5}, {-5, -5}
    };

    private String scenario = "idle";

    @Override
    public void onEnable() {
        scenario = System.getProperty("yap.bench.scenario", "idle").toLowerCase(Locale.ROOT);
        int seconds = Integer.getInteger("yap.bench.seconds", 30);
        int warmup = Integer.getInteger("yap.bench.warmup", 10);
        String label = System.getProperty("yap.bench.label", "run");
        String out = System.getProperty("yap.bench.out", "bench/results/last.json");

        getLogger().info("MSPT bench scenario=" + scenario + " warmup=" + warmup
                + "s sample=" + seconds + "s label=" + label);

        if ("highpop".equals(scenario) || "fullcite".equals(scenario)) {
            Bukkit.getPluginManager().registerEvents(this, this);
        }

        YapSched.globalLater(this, () -> {
            World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
            if (world == null) {
                getLogger().severe("No world — aborting bench");
                Bukkit.shutdown();
                return;
            }
            if ("highpop".equals(scenario) || "fullcite".equals(scenario)) {
                if (YapSched.isRegionized()) {
                    BenchRegionLoad.preparePopBench(this, world, scenario, expectedTnt ->
                            startPopBench(world, scenario, label, out, warmup, seconds, expectedTnt));
                } else {
                    int expectedTnt = "fullcite".equals(scenario)
                            ? prepareFullcite(world)
                            : prepareHighpop(world);
                    startPopBench(world, scenario, label, out, warmup, seconds, expectedTnt);
                }
            } else if (YapSched.isRegionized()) {
                // Folia/Canvas: chunk mutation must be region-owned (not GlobalRegionScheduler).
                BenchRegionLoad.prepare(this, world, scenario, expectedTnt ->
                        YapSched.globalLater(this, () ->
                                        sampleAndWrite(world, scenario, label, out, warmup, seconds, expectedTnt),
                                warmup * 20L));
            } else {
                int expectedTnt = prepare(world, scenario);
                YapSched.globalLater(this, () ->
                                sampleAndWrite(world, scenario, label, out, warmup, seconds, expectedTnt),
                        warmup * 20L);
            }
        }, 40L);
    }

    /**
     * Scatter yapbots into deep quads on join so they do not path from spawn across
     * origin borders (that path alone burned Yap spatial barrier MSPT).
     */
    @EventHandler
    public void onBotJoin(PlayerJoinEvent event) {
        if (!"highpop".equals(scenario) && !"fullcite".equals(scenario)) {
            return;
        }
        Player p = event.getPlayer();
        String name = p.getName();
        if (!name.startsWith("yapbot_")) {
            return;
        }
        int id;
        try {
            id = Integer.parseInt(name.substring("yapbot_".length()));
        } catch (NumberFormatException e) {
            return;
        }
        int[] xz = BOT_HOME_XZ[Math.floorMod(id, BOT_HOME_XZ.length)];
        World world = p.getWorld();
        int y = world.getHighestBlockYAt(xz[0], xz[1]) + 1;
        Location dest = new Location(world, xz[0] + 0.5, y, xz[1] + 0.5);
        YapSched.entity(this, p, () -> {
            if (p.isOnline()) {
                p.teleport(dest);
            }
        });
    }

    private int prepare(World world, String scenario) {
        for (int[] c : INTERIOR) {
            world.getChunkAt(c[0], c[1]).load(true);
            world.setChunkForceLoaded(c[0], c[1], true);
            world.getChunkAt(c[0], c[1]).getEntities();
        }
        for (Entity e : world.getEntities()) {
            if (!(e instanceof Player)) {
                e.remove();
            }
        }
        return switch (scenario) {
            case "entity" -> {
                int per = Integer.getInteger("yap.bench.entities", 120);
                spawnPrimedTnt(world, per);
                yield per * 4;
            }
            case "farm" -> {
                plantFarms(world);
                yield 0;
            }
            case "heavypop" -> {
                int entities = Integer.getInteger("yap.bench.entities", 1200);
                int hoppers = Integer.getInteger("yap.bench.hoppers", 256);
                spawnPrimedTnt(world, entities);
                placeHeavyHoppers(world, hoppers);
                getLogger().info("heavypop ready — TNT/quad=" + entities + " hoppers/quad=" + hoppers
                        + " totalTNT=" + (entities * 4) + " totalHoppers=" + (hoppers * 4));
                yield entities * 4;
            }
            default -> {
                getLogger().info("Idle scenario — no load injected (regression guard only)");
                yield 0;
            }
        };
    }

    private int prepareHighpop(World world) {
        world.setSpawnLocation(0, Math.max(world.getHighestBlockYAt(0, 0) + 1, 80), 0);
        for (int[] c : INTERIOR) {
            world.getChunkAt(c[0], c[1]).load(true);
            world.setChunkForceLoaded(c[0], c[1], true);
        }
        for (int[] c : DEEP_HOME_CHUNKS) {
            world.getChunkAt(c[0], c[1]).load(true);
            world.setChunkForceLoaded(c[0], c[1], true);
        }
        for (Entity e : world.getEntities()) {
            if (!(e instanceof Player)) {
                e.remove();
            }
        }
        enforceEntityParity(world, true);
        // Farms + hoppers + chests + villagers + animals + redstone clocks + nether portals stubs
        plantFarms(world);
        placeHoppers(world, Integer.getInteger("yap.bench.hoppers", 64));
        placeChestsAndVillagers(world);
        placeAnimals(world);
        placeRedstoneClocks(world);
        placeBorderMarkers(world);
        writeReadyMarker(world);
        getLogger().info("highpop world fixtures ready — waiting for bots "
                + "(target players=" + Integer.getInteger("yap.bench.players", 100) + ")");
        return 0;
    }

    /**
     * Product cite: highpop bot path + deep-interior heavypop TNT/hoppers (not on
     * border chunks) so Paper/Leaf climb toward tick budget while bots stay online.
     */
    private int prepareFullcite(World world) {
        prepareHighpop(world);
        int entities = Integer.getInteger("yap.bench.entities", 600);
        int heavyHoppers = Integer.getInteger("yap.bench.heavy_hoppers",
                Integer.getInteger("yap.bench.hoppers", 128));
        // Force-load deep interior piles used by heavypop
        for (int[][] piles : HEAVY_PILES) {
            for (int[] c : piles) {
                world.getChunkAt(c[0], c[1]).load(true);
                world.setChunkForceLoaded(c[0], c[1], true);
            }
        }
        spawnPrimedTnt(world, entities);
        placeHeavyHoppers(world, heavyHoppers);
        int expected = entities * 4;
        getLogger().info("fullcite ready — bots + TNT/quad=" + entities
                + " heavyHoppers/quad=" + heavyHoppers
                + " totalTNT=" + expected
                + " (deep interior; border-safe geometry)");
        return expected;
    }

    private void startPopBench(World world, String scenario, String label, String out,
                               int warmup, int sampleSec, int expectedTnt) {
        writeReadyMarker(world);
        if ("fullcite".equals(scenario)) {
            getLogger().info("fullcite ready — bots + expectedTNT=" + expectedTnt
                    + " (region-safe prep)");
        } else {
            getLogger().info("highpop world fixtures ready — waiting for bots "
                    + "(target players=" + Integer.getInteger("yap.bench.players", 100) + ")");
        }
        waitForBotsThenSample(world, scenario, label, out, warmup, sampleSec, expectedTnt);
    }

    private void writeReadyMarker(World world) {
        try {
        String rootProp = System.getProperty("yap.bench.root",
                System.getProperty("yapcore.home", "."));
        Path home = Path.of(rootProp).toAbsolutePath().normalize();
            Path marker = home.resolve("bench").resolve("highpop-ready.port");
            Files.createDirectories(marker.getParent());
            // Public bot connect port (Via front) may differ from Bukkit.getPort() (Paper loopback).
            int paperPort = Bukkit.getPort();
            int botPort = Integer.getInteger("yap.bench.bot_port", paperPort);
            String body = botPort + "\n" + world.getName() + "\n" + Instant.now()
                    + "\npaper_port=" + paperPort + "\n";
            Files.writeString(marker, body, StandardCharsets.UTF_8);
            Files.writeString(Path.of("yap-bench-ready.port"), body, StandardCharsets.UTF_8);
            getLogger().info("ready marker → " + marker.toAbsolutePath()
                    + " bot_port=" + botPort + " paper_port=" + paperPort);
        } catch (IOException e) {
            getLogger().severe("ready marker write failed: " + e.getMessage());
        }
    }

    private void waitForBotsThenSample(World world, String scenario, String label, String out,
                                       int warmupSec, int sampleSec, int expectedTnt) {
        int target = Integer.getInteger("yap.bench.players", 100);
        int joinTimeout = Integer.getInteger("yap.bench.join_timeout", 180);
        // Match players_ok: start sample once ≥90% are online (or timeout).
        int need = Math.max(1, (int) Math.ceil(target * 0.90));
        // Require a stable hold before warmup — avoids sampling while still joining/dropping.
        int stableNeed = Integer.getInteger("yap.bench.join_stable_sec", 15);
        final int[] waited = {0};
        final int[] stable = {0};
        final YapTask[] joinGate = new YapTask[1];
        joinGate[0] = YapSched.globalTimer(this, () -> {
            int online = Bukkit.getOnlinePlayers().size();
            waited[0]++;
            if (online >= need) {
                stable[0]++;
            } else {
                stable[0] = 0;
            }
            boolean ready = stable[0] >= stableNeed;
            boolean timedOut = waited[0] >= joinTimeout;
            if (ready || timedOut) {
                joinGate[0].cancel();
                getLogger().info(scenario + " join gate — online=" + online + "/" + target
                        + " need≥" + need + " stable=" + stable[0] + "s/" + stableNeed
                        + "s waited=" + waited[0] + "s"
                        + (timedOut && !ready ? " (TIMEOUT)" : "")
                        + " — starting warmup " + warmupSec + "s");
                // After warmup, require population still held (physics enable often
                // drops bots; sampling at the trough gamed MSPT / failed fairness).
                YapSched.globalLater(this, () ->
                                waitPostWarmupThenSample(world, scenario, label, out,
                                        warmupSec, sampleSec, expectedTnt, need, target),
                        warmupSec * 20L);
            } else if (waited[0] % 10 == 0) {
                getLogger().info("waiting for bots… online=" + online + "/" + target
                        + " (need≥" + need + ", stable " + stable[0] + "/" + stableNeed + "s)");
            }
        }, 20L, 20L);
    }

    private void waitPostWarmupThenSample(World world, String scenario, String label, String out,
                                          int warmupSec, int sampleSec, int expectedTnt,
                                          int need, int target) {
        int settleTimeout = Integer.getInteger("yap.bench.post_warmup_sec", 90);
        final int[] waited = {0};
        final int[] stable = {0};
        int stableNeed = Integer.getInteger("yap.bench.post_warmup_stable_sec", 10);
        final YapTask[] settleGate = new YapTask[1];
        settleGate[0] = YapSched.globalTimer(this, () -> {
            int online = Bukkit.getOnlinePlayers().size();
            waited[0]++;
            if (online >= need) {
                stable[0]++;
            } else {
                stable[0] = 0;
            }
            if (stable[0] >= stableNeed || waited[0] >= settleTimeout) {
                settleGate[0].cancel();
                getLogger().info(scenario + " post-warmup gate — online=" + online + "/" + target
                        + " need≥" + need + " stable=" + stable[0] + "s/" + stableNeed
                        + "s waited=" + waited[0] + "s — starting sample " + sampleSec + "s");
                // Same entity budget across forks: no wild mobs / item-XP drift from bot combat.
                if (YapSched.isRegionized()) {
                    YapSched.global(this, () -> world.setGameRule(GameRule.SPAWN_MONSTERS, false));
                } else {
                    enforceEntityParity(world, false);
                }
                sampleAndWrite(world, scenario, label, out, warmupSec, sampleSec, expectedTnt);
            } else if (waited[0] % 10 == 0) {
                getLogger().info("post-warmup settle… online=" + online + "/" + target
                        + " (need≥" + need + ", stable " + stable[0] + "/" + stableNeed + "s)");
            }
        }, 20L, 20L);
    }

    private void spawnPrimedTnt(World world, int perQuad) {
        int pilesNeeded = Math.max(1, (perQuad + MAX_TNT_PER_CHUNK - 1) / MAX_TNT_PER_CHUNK);
        for (int[][] piles : HEAVY_PILES) {
            int remaining = perQuad;
            int use = Math.min(pilesNeeded, piles.length);
            for (int p = 0; p < use; p++) {
                int[] c = piles[p];
                int n = remaining / (use - p);
                remaining -= n;
                world.getChunkAt(c[0], c[1]).load(true);
                world.setChunkForceLoaded(c[0], c[1], true);
                int bx = (c[0] << 4) + 8;
                int bz = (c[1] << 4) + 8;
                int y = Math.max(world.getHighestBlockYAt(bx, bz) + 2, 80);
                for (int i = 0; i < n; i++) {
                    TNTPrimed tnt = world.spawn(
                            new Location(world, bx + (i % 8) * 0.1, y + (i / 64) * 0.2, bz + (i / 8) * 0.1),
                            TNTPrimed.class);
                    tnt.setFuseTicks(20 * 60 * 10);
                    tnt.setYield(0f);
                    tnt.setIsIncendiary(false);
                }
            }
        }
        getLogger().info("Spawned primed TNT ×4 quads, " + perQuad + "/quad across "
                + pilesNeeded + " deep-interior pile(s)/quad (cap " + MAX_TNT_PER_CHUNK + "/chunk)");
    }

    private void plantFarms(World world) {
        for (int[] c : INTERIOR) {
            world.getChunkAt(c[0], c[1]).load(true);
            world.setChunkForceLoaded(c[0], c[1], true);
            int bx = c[0] << 4;
            int bz = c[1] << 4;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int y = world.getHighestBlockYAt(bx + x, bz + z);
                    world.getBlockAt(bx + x, y, bz + z).setType(Material.FARMLAND);
                    world.getBlockAt(bx + x, y + 1, bz + z).setType(Material.WHEAT);
                }
            }
        }
        getLogger().info("Planted wheat farms in 4 interior quads");
    }

    /** Hoppers co-located with first heavy pile per quad (heavypop). */
    private void placeHeavyHoppers(World world, int perQuad) {
        for (int[][] piles : HEAVY_PILES) {
            int[] c = piles[0];
            world.getChunkAt(c[0], c[1]).load(true);
            world.setChunkForceLoaded(c[0], c[1], true);
            int bx = c[0] << 4;
            int bz = c[1] << 4;
            int y = Math.max(world.getHighestBlockYAt(bx + 2, bz + 2), 64);
            for (int i = 0; i < perQuad; i++) {
                int x = bx + (i % 16);
                int z = bz + ((i / 16) % 16);
                int yy = y + (i / 256);
                world.getBlockAt(x, yy, z).setType(Material.STONE);
                world.getBlockAt(x, yy + 1, z).setType(Material.HOPPER);
            }
        }
        getLogger().info("Placed hoppers in 4 heavy-pile chunks x" + perQuad);
    }

    private void placeHoppers(World world, int perQuad) {
        for (int[] c : INTERIOR) {
            world.getChunkAt(c[0], c[1]).load(true);
            world.setChunkForceLoaded(c[0], c[1], true);
            int bx = c[0] << 4;
            int bz = c[1] << 4;
            int y = Math.max(world.getHighestBlockYAt(bx + 2, bz + 2), 64);
            for (int i = 0; i < perQuad; i++) {
                int x = bx + (i % 16);
                int z = bz + ((i / 16) % 16);
                int yy = y + (i / 256);
                world.getBlockAt(x, yy, z).setType(Material.STONE);
                world.getBlockAt(x, yy + 1, z).setType(Material.HOPPER);
            }
        }
        getLogger().info("Placed hoppers in 4 interior quads x" + perQuad);
    }

    private void placeChestsAndVillagers(World world) {
        int villagers = Integer.getInteger("yap.bench.villagers", 32);
        for (int[] c : INTERIOR) {
            int bx = (c[0] << 4) + 4;
            int bz = (c[1] << 4) + 4;
            int y = Math.max(world.getHighestBlockYAt(bx, bz), 64) + 1;
            world.getBlockAt(bx, y, bz).setType(Material.CHEST);
            world.getBlockAt(bx + 1, y, bz).setType(Material.CHEST);
            for (int i = 0; i < villagers / 4; i++) {
                world.spawnEntity(
                        new Location(world, bx + 0.5, y, bz + 2.5 + i * 0.3), EntityType.VILLAGER);
            }
        }
        getLogger().info("Placed chests + villagers (~" + villagers + ")");
    }

    private void placeAnimals(World world) {
        int n = Integer.getInteger("yap.bench.animals", 48);
        EntityType[] types = {EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN};
        int spawned = 0;
        for (int[] c : INTERIOR) {
            int bx = (c[0] << 4) + 10;
            int bz = (c[1] << 4) + 10;
            int y = Math.max(world.getHighestBlockYAt(bx, bz), 64) + 1;
            for (int i = 0; i < n / 4; i++) {
                Entity e = world.spawnEntity(new Location(world, bx + (i % 4), y, bz + (i / 4)),
                        types[i % types.length]);
                // Lock breeding — Yap spatial kept animals fully awake under 4-quad
                // player proximity and inflated entity counts vs Paper/Leaf (~+170).
                if (e instanceof org.bukkit.entity.Animals a) {
                    a.setAdult();
                    a.setAgeLock(true);
                    a.setBreed(false);
                }
                spawned++;
            }
        }
        getLogger().info("Spawned animals (~" + spawned + ", breed locked)");
    }

    /**
     * Keep entity counts comparable across Paper/Leaf/Yap with identical plugins.
     * Prior active150 MT cites showed Yap ~+170–200 entities (extra hostiles + ITEM
     * from awake spatial animals / bot combat) while spawn-monsters=false alone
     * still left creepers/zombies in the world.
     *
     * @param fixturesOnly when true (prepare): only gamerules; entities already cleared
     */
    @SuppressWarnings("removal")
    private void enforceEntityParity(World world, boolean fixturesOnly) {
        world.setGameRule(GameRule.SPAWN_MONSTERS, false);
        if (fixturesOnly) {
            getLogger().info("Entity parity: SPAWN_MONSTERS=false");
            return;
        }
        int removedHostiles = 0;
        int removedTransient = 0;
        int relocked = 0;
        for (Entity e : List.copyOf(world.getEntities())) {
            if (e instanceof Player) {
                continue;
            }
            if (e instanceof Monster) {
                e.remove();
                removedHostiles++;
                continue;
            }
            EntityType t = e.getType();
            if (t == EntityType.ITEM || t == EntityType.EXPERIENCE_ORB
                    || t == EntityType.ARROW || t == EntityType.SPECTRAL_ARROW
                    || t == EntityType.TRIDENT) {
                e.remove();
                removedTransient++;
                continue;
            }
            if (e instanceof org.bukkit.entity.Animals a) {
                a.setAdult();
                a.setAgeLock(true);
                a.setBreed(false);
                relocked++;
            }
        }
        getLogger().info("Entity parity trim hostiles=" + removedHostiles
                + " transient=" + removedTransient + " animalsRelocked=" + relocked);
    }

    private void placeRedstoneClocks(World world) {
        for (int[] c : INTERIOR) {
            int bx = (c[0] << 4) + 1;
            int bz = (c[1] << 4) + 1;
            int y = Math.max(world.getHighestBlockYAt(bx, bz), 64) + 2;
            // Compact observer clock (always-on tick noise)
            world.getBlockAt(bx, y, bz).setType(Material.OBSERVER);
            world.getBlockAt(bx, y + 1, bz).setType(Material.OBSERVER);
            world.getBlockAt(bx + 1, y, bz).setType(Material.REDSTONE_LAMP);
            world.getBlockAt(bx + 1, y + 1, bz).setType(Material.REDSTONE_BLOCK);
        }
        getLogger().info("Placed observer/redstone clocks in 4 quads");
    }

    private void placeBorderMarkers(World world) {
        // Chests in deep homes so bots have inventory targets without crossing origin borders
        for (int[] c : DEEP_HOME_CHUNKS) {
            int bx = (c[0] << 4) + 8;
            int bz = (c[1] << 4) + 8;
            int y = Math.max(world.getHighestBlockYAt(bx, bz), 64) + 1;
            Block b = world.getBlockAt(bx, y, bz);
            b.setType(Material.CHEST);
        }
    }

    private record LoadSnapshot(int tntAlive, double fuseMean, int hoppers, int entitiesTotal,
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
        for (int[][] piles : HEAVY_PILES) {
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
        for (int[] c : INTERIOR) {
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

    private void sampleAndWrite(World world, String scenario, String label, String out,
                                int warmupSec, int sampleSec, int expectedTnt) {
        if (YapSched.isRegionized()) {
            BenchRegionLoad.snapshotAsync(this, world, start -> {
                getLogger().info("Load@sample-start players=" + start.players()
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
        getLogger().info("Load@sample-start players=" + start.players()
                + " entities=" + start.entitiesTotal()
                + " villagers=" + start.villagers()
                + " hoppers=" + start.hoppers()
                + " chunks=" + start.loadedChunks()
                + " tnt=" + start.tntAlive()
                + " types=" + start.entityTop());
        runSampler(world, scenario, label, out, warmupSec, sampleSec, expectedTnt, start);
    }

    private static LoadSnapshot toLegacySnapshot(BenchRegionLoad.LoadSnapshot s) {
        return new LoadSnapshot(s.tntAlive(), s.fuseMean(), s.hoppers(), s.entitiesTotal(),
                s.players(), s.villagers(), s.loadedChunks(), s.entityTop());
    }

    private void runSampler(World world, String scenario, String label, String out,
                            int warmupSec, int sampleSec, int expectedTnt, LoadSnapshot start) {
        List<Double> mspt = new ArrayList<>();
        List<Double> tps1m = new ArrayList<>();
        final int[] left = {sampleSec};
        final YapTask[] sampler = new YapTask[1];
        sampler[0] = YapSched.globalTimer(this, () -> {
            mspt.add(Bukkit.getServer().getAverageTickTime());
            double[] tps = Bukkit.getServer().getTPS();
            tps1m.add(tps.length > 0 ? tps[0] : 0);
            left[0]--;
            if (left[0] <= 0) {
                sampler[0].cancel();
                if (YapSched.isRegionized()) {
                    BenchRegionLoad.snapshotAsync(this, world, endR -> {
                        LoadSnapshot end = toLegacySnapshot(endR);
                        getLogger().info("Load@sample-end players=" + end.players()
                                + " entities=" + end.entitiesTotal()
                                + " chunks=" + end.loadedChunks());
                        writeJson(scenario, label, out, warmupSec, sampleSec, mspt, tps1m,
                                expectedTnt, start, end);
                        getLogger().info("Bench complete — shutting down");
                        YapSched.globalLater(this, Bukkit::shutdown, 20L);
                    });
                } else {
                    LoadSnapshot end = snapshotLoad(world);
                    getLogger().info("Load@sample-end players=" + end.players()
                            + " entities=" + end.entitiesTotal()
                            + " chunks=" + end.loadedChunks());
                    writeJson(scenario, label, out, warmupSec, sampleSec, mspt, tps1m,
                            expectedTnt, start, end);
                    getLogger().info("Bench complete — shutting down");
                    YapSched.globalLater(this, Bukkit::shutdown, 20L);
                }
            }
        }, 20L, 20L);
    }

    private void writeJson(String scenario, String label, String outPath,
                           int warmup, int sampleSec, List<Double> mspt, List<Double> tps,
                           int expectedTnt, LoadSnapshot start, LoadSnapshot end) {
        double mean = mean(mspt);
        double p50 = percentile(mspt, 0.50);
        double p95 = percentile(mspt, 0.95);
        double tpsMean = mean(tps);
        double fuseDrop = start.fuseMean() - end.fuseMean();
        double expectedFuseDrop = sampleSec * 20.0;
        boolean fuseOk = start.tntAlive() == 0
                || (fuseDrop >= expectedFuseDrop * 0.50 && end.tntAlive() >= start.tntAlive() * 0.98);
        int targetPlayers = Integer.getInteger("yap.bench.players", 0);
        // Highpop / fullcite: must HOLD population through the sample, not just
        // clear the join gate. Start+end ≥90% of target — bleeding 250→130 fails.
        int needHold = Math.max(1, (int) Math.ceil(targetPlayers * 0.90));
        boolean playersOk = (!"highpop".equals(scenario) && !"fullcite".equals(scenario))
                || (targetPlayers <= 0)
                || (start.players() >= needHold && end.players() >= needHold);
        // cite-stable = keepalive-only swarm (physics OFF). Not an MSPT gameplay cite.
        String botLoad = System.getProperty("yap.bench.bot_load", "active");
        if (botLoad == null || botLoad.isBlank()) {
            botLoad = "active";
        }
        String measurementScope = System.getProperty("yap.bench.measurement_scope", "game_tick_mspt");
        String gameXms = System.getProperty("yap.bench.game_xms", "");
        String gameXmx = System.getProperty("yap.bench.game_xmx", "");
        long gameJvmMaxMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        boolean chassisPresent = Boolean.parseBoolean(System.getProperty("yap.bench.chassis_present", "false"));
        String json = """
                {
                  "label": %s,
                  "scenario": %s,
                  "warmup_seconds": %d,
                  "sample_seconds": %d,
                  "samples": %d,
                  "mspt_mean": %.4f,
                  "mspt_p50": %.4f,
                  "mspt_p95": %.4f,
                  "tps_1m_mean": %.4f,
                  "measurement_scope": %s,
                  "game_jvm_xms": %s,
                  "game_jvm_xmx": %s,
                  "game_jvm_max_mb": %d,
                  "chassis_present": %s,
                  "expected_tnt": %d,
                  "tnt_start": %d,
                  "tnt_end": %d,
                  "fuse_mean_start": %.2f,
                  "fuse_mean_end": %.2f,
                  "fuse_drop": %.2f,
                  "fuse_drop_expected": %.2f,
                  "fuse_ticking_ok": %s,
                  "hoppers_start": %d,
                  "hoppers_end": %d,
                  "entities_start": %d,
                  "entities_end": %d,
                  "players_start": %d,
                  "players_end": %d,
                  "players_target": %d,
                  "players_ok": %s,
                  "bot_load": %s,
                  "villagers_start": %d,
                  "chunks_loaded_start": %d,
                  "chunks_loaded_end": %d,
                  "entity_top_start": %s,
                  "entity_top_end": %s,
                  "timestamp": %s,
                  "java": %s
                }
                """.formatted(
                quote(label),
                quote(scenario),
                warmup,
                sampleSec,
                mspt.size(),
                mean,
                p50,
                p95,
                tpsMean,
                quote(measurementScope),
                quote(gameXms),
                quote(gameXmx),
                gameJvmMaxMb,
                chassisPresent,
                expectedTnt,
                start.tntAlive(),
                end.tntAlive(),
                start.fuseMean(),
                end.fuseMean(),
                fuseDrop,
                expectedFuseDrop,
                fuseOk,
                start.hoppers(),
                end.hoppers(),
                start.entitiesTotal(),
                end.entitiesTotal(),
                start.players(),
                end.players(),
                targetPlayers,
                playersOk,
                quote(botLoad),
                start.villagers(),
                start.loadedChunks(),
                end.loadedChunks(),
                quote(start.entityTop()),
                quote(end.entityTop()),
                quote(Instant.now().toString()),
                quote(System.getProperty("java.version", "?"))
        );
        try {
            Path p = Path.of(outPath);
            if (!p.isAbsolute()) {
                String home = System.getProperty("yapcore.home");
                if (home != null && !home.isBlank()) {
                    p = Path.of(home).resolve(outPath);
                }
            }
            Files.createDirectories(p.getParent());
            Files.writeString(p, json, StandardCharsets.UTF_8);
            getLogger().info("Wrote " + p.toAbsolutePath()
                    + " mspt_mean=" + String.format(Locale.ROOT, "%.3f", mean)
                    + " players=" + start.players()
                    + " tps=" + String.format(Locale.ROOT, "%.2f", tpsMean));
        } catch (IOException e) {
            getLogger().severe("Failed to write results: " + e.getMessage());
        }
    }

    private static double mean(List<Double> v) {
        if (v.isEmpty()) {
            return 0;
        }
        double s = 0;
        for (double d : v) {
            s += d;
        }
        return s / v.size();
    }

    private static double percentile(List<Double> v, double p) {
        if (v.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(v);
        sorted.sort(Double::compareTo);
        int i = Math.min(sorted.size() - 1, Math.max(0, (int) Math.round(p * (sorted.size() - 1))));
        return sorted.get(i);
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}

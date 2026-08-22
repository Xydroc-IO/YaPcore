package com.yapcore.bench;

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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

final class BenchWorldPrep {
    /** Near-spawn interiors for highpop fixtures (within view-distance). */
    static final int[][] INTERIOR = {
            {2, 2}, {-3, 2}, {2, -3}, {-3, -3}
    };

    /**
     * Deep bot homes (block xz) — one per spatial quadrant, |xz|≥64 so chunk≥4.
     * Matches swarm.js QUAD_WAYPOINTS; keeps bots off origin-border planes while
     * still exercising all four MT owners (not SE-only).
     */
    static final int[][] BOT_HOME_XZ = {
            {72, 72}, {-72, 72}, {72, -72}, {-72, -72}
    };

    /**
     * Heavypop TNT/hopper piles — deep interior, first pile is the classic winning
     * mid-load geometry. Cap ~600 primed TNT/chunk before fuse proofs fail on stock;
     * extra piles stay inside the same quadrant (no border-adjacent 3×3 halo).
     */
    static final int[][][] HEAVY_PILES = {
            {{8, 8}, {12, 12}, {8, 12}, {12, 8}},
            {{-9, 8}, {-13, 12}, {-9, 12}, {-13, 8}},
            {{8, -9}, {12, -13}, {8, -13}, {12, -9}},
            {{-9, -9}, {-13, -13}, {-9, -13}, {-13, -9}},
    };
    static final int MAX_TNT_PER_CHUNK = 600;

    /** Chunk coords of deep bot homes — force-load + inventory targets (not origin-border). */
    static final int[][] DEEP_HOME_CHUNKS = {
            {4, 4}, {-5, 4}, {4, -5}, {-5, -5}
    };

    private final JavaPlugin plugin;

    BenchWorldPrep(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    static int[][] botHomeXz() {
        return BOT_HOME_XZ;
    }

    int prepare(World world, String scenario) {
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
                plugin.getLogger().info("heavypop ready — TNT/quad=" + entities + " hoppers/quad=" + hoppers
                        + " totalTNT=" + (entities * 4) + " totalHoppers=" + (hoppers * 4));
                yield entities * 4;
            }
            default -> {
                plugin.getLogger().info("Idle scenario — no load injected (regression guard only)");
                yield 0;
            }
        };
    }

    int prepareHighpop(World world) {
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
        plugin.getLogger().info("highpop world fixtures ready — waiting for bots "
                + "(target players=" + Integer.getInteger("yap.bench.players", 100) + ")");
        return 0;
    }

    int prepareFullcite(World world) {
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
        plugin.getLogger().info("fullcite ready — bots + TNT/quad=" + entities
                + " heavyHoppers/quad=" + heavyHoppers
                + " totalTNT=" + expected
                + " (deep interior; border-safe geometry)");
        return expected;
    }

    void spawnPrimedTnt(World world, int perQuad) {
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
        plugin.getLogger().info("Spawned primed TNT ×4 quads, " + perQuad + "/quad across "
                + pilesNeeded + " deep-interior pile(s)/quad (cap " + MAX_TNT_PER_CHUNK + "/chunk)");
    }

    void plantFarms(World world) {
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
        plugin.getLogger().info("Planted wheat farms in 4 interior quads");
    }

    void placeHeavyHoppers(World world, int perQuad) {
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
        plugin.getLogger().info("Placed hoppers in 4 heavy-pile chunks x" + perQuad);
    }

    void placeHoppers(World world, int perQuad) {
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
        plugin.getLogger().info("Placed hoppers in 4 interior quads x" + perQuad);
    }

    void placeChestsAndVillagers(World world) {
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
        plugin.getLogger().info("Placed chests + villagers (~" + villagers + ")");
    }

    void placeAnimals(World world) {
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
        plugin.getLogger().info("Spawned animals (~" + spawned + ", breed locked)");
    }

    void enforceEntityParity(World world, boolean fixturesOnly) {
        world.setGameRule(GameRule.SPAWN_MONSTERS, false);
        if (fixturesOnly) {
            plugin.getLogger().info("Entity parity: SPAWN_MONSTERS=false");
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
        plugin.getLogger().info("Entity parity trim hostiles=" + removedHostiles
                + " transient=" + removedTransient + " animalsRelocked=" + relocked);
    }

    void placeRedstoneClocks(World world) {
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
        plugin.getLogger().info("Placed observer/redstone clocks in 4 quads");
    }

    void placeBorderMarkers(World world) {
        // Chests in deep homes so bots have inventory targets without crossing origin borders
        for (int[] c : DEEP_HOME_CHUNKS) {
            int bx = (c[0] << 4) + 8;
            int bz = (c[1] << 4) + 8;
            int y = Math.max(world.getHighestBlockYAt(bx, bz), 64) + 1;
            Block b = world.getBlockAt(bx, y, bz);
            b.setType(Material.CHEST);
        }
    }
}

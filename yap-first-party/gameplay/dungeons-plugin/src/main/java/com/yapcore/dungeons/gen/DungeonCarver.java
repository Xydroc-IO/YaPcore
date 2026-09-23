package com.yapcore.dungeons.gen;

import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Applies a planned layout into a Bukkit world. */
public final class DungeonCarver {

    public static final String BOSS_PDC_KEY = "yap_dungeon_boss";
    public static final String MOB_PDC_KEY = "yap_dungeon_mob";

    private final JavaPlugin plugin;
    private final RoomTemplates templates;

    public DungeonCarver(JavaPlugin plugin) {
        this.plugin = plugin;
        this.templates = new RoomTemplates(plugin);
    }

    public record BuildResult(Location entrance, Location bossArena, List<Location> chestLocations) {
    }

    public CompletableFuture<BuildResult> carve(
            World world,
            RoomGraphBuilder.Layout layout,
            ThemeTable.Theme theme,
            DifficultyTable.LevelDiff diff,
            long seed,
            String runId,
            Consumer<String> progress) {
        CompletableFuture<BuildResult> future = new CompletableFuture<>();
        Random rng = new Random(seed ^ 0xD00DL);
        RoomGraphBuilder.Room entranceRoom = layout.rooms().getFirst();
        Location entrance = new Location(world,
                entranceRoom.centerX() + 0.5,
                layout.originY() + 1,
                entranceRoom.centerZ() + 0.5,
                0.0f,
                25.0f);
        RoomGraphBuilder.Room bossRoom = layout.rooms().getLast();
        Location bossLoc = new Location(world,
                bossRoom.centerX() + 0.5,
                layout.originY() + 1,
                bossRoom.centerZ() + 0.5);
        List<Location> chests = new ArrayList<>();
        Map<Integer, RoomGraphBuilder.Room> byId = new HashMap<>();
        for (RoomGraphBuilder.Room r : layout.rooms()) {
            byId.put(r.id(), r);
        }

        int minX = layout.minX() - 2;
        int minZ = layout.minZ() - 2;
        int maxX = layout.maxX() + 2;
        int maxZ = layout.maxZ() + 2;

        progress.accept("Laying foundation…");
        forEachChunk(world, minX, minZ, maxX, maxZ, (cx, cz) ->
                layFoundationInChunk(world, layout, theme, cx, cz))
                .thenCompose(v -> {
                    progress.accept("Carving rooms…");
                    return forEachChunk(world, minX, minZ, maxX, maxZ, (cx, cz) -> {
                        for (RoomGraphBuilder.Room room : layout.rooms()) {
                            carveRoomShellInChunk(world, room, layout.originY(), theme, cx, cz);
                        }
                    });
                })
                .thenCompose(v -> {
                    progress.accept("Connecting corridors…");
                    return forEachChunk(world, minX, minZ, maxX, maxZ, (cx, cz) -> {
                        for (RoomGraphBuilder.Corridor c : layout.corridors()) {
                            RoomGraphBuilder.Room a = byId.get(c.fromId());
                            RoomGraphBuilder.Room b = byId.get(c.toId());
                            if (a == null || b == null) {
                                continue;
                            }
                            carveCorridorInChunk(world, a, b, layout.originY(), theme, cx, cz);
                        }
                    });
                })
                .thenCompose(v -> {
                    // Doorways + decorate touch a room's footprint — run per room on its region
                    progress.accept("Decorating rooms…");
                    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                    for (RoomGraphBuilder.Corridor c : layout.corridors()) {
                        RoomGraphBuilder.Room a = byId.get(c.fromId());
                        RoomGraphBuilder.Room b = byId.get(c.toId());
                        if (a == null || b == null) {
                            continue;
                        }
                        chain = chain.thenCompose(ignored -> regionRun(world, a.centerX(), a.centerZ(), () ->
                                templates.carveDoorway(world, a, layout.originY(), b.centerX(), b.centerZ(), theme)));
                        chain = chain.thenCompose(ignored -> regionRun(world, b.centerX(), b.centerZ(), () ->
                                templates.carveDoorway(world, b, layout.originY(), a.centerX(), a.centerZ(), theme)));
                    }
                    for (RoomGraphBuilder.Room room : layout.rooms()) {
                        chain = chain.thenCompose(ignored -> regionRun(world, room.centerX(), room.centerZ(), () ->
                                templates.decorate(world, room, layout.originY(), theme, rng, runId, chests)));
                    }
                    return chain;
                })
                .thenCompose(v -> {
                    // Re-punch corridors after decor — cover walls / bars used to seal the path
                    progress.accept("Opening paths…");
                    return forEachChunk(world, minX, minZ, maxX, maxZ, (cx, cz) -> {
                        for (RoomGraphBuilder.Corridor c : layout.corridors()) {
                            RoomGraphBuilder.Room a = byId.get(c.fromId());
                            RoomGraphBuilder.Room b = byId.get(c.toId());
                            if (a == null || b == null) {
                                continue;
                            }
                            carveCorridorInChunk(world, a, b, layout.originY(), theme, cx, cz);
                        }
                    });
                })
                .thenCompose(v -> {
                    progress.accept("Spawning hostiles…");
                    CompletableFuture<Void> chain = regionRun(world, entrance.getBlockX(), entrance.getBlockZ(),
                            () -> world.setSpawnLocation(entrance));
                    for (RoomGraphBuilder.Room room : layout.rooms()) {
                        if (room.kind() == RoomGraphBuilder.RoomKind.ENTRANCE) {
                            continue;
                        }
                        if (room.kind() == RoomGraphBuilder.RoomKind.BOSS) {
                            chain = chain.thenCompose(ignored -> regionRun(world, bossLoc.getBlockX(), bossLoc.getBlockZ(),
                                    () -> spawnBoss(world, bossLoc, theme, diff, runId)));
                        } else if (room.kind() == RoomGraphBuilder.RoomKind.COMBAT
                                || room.kind() == RoomGraphBuilder.RoomKind.TRAP) {
                            chain = chain.thenCompose(ignored -> regionRun(world, room.centerX(), room.centerZ(),
                                    () -> spawnMobs(world, room, layout.originY(), theme, diff, rng, runId)));
                        }
                    }
                    return chain;
                })
                .thenCompose(v -> {
                    progress.accept("Ready…");
                    return regionRun(world, entrance.getBlockX(), entrance.getBlockZ(), () ->
                            world.refreshChunk(entrance.getBlockX() >> 4, entrance.getBlockZ() >> 4));
                })
                .whenComplete((v, err) -> {
                    if (err != null) {
                        future.completeExceptionally(err instanceof Exception e ? e : new Exception(err));
                    } else {
                        future.complete(new BuildResult(entrance, bossLoc, List.copyOf(chests)));
                    }
                });
        return future;
    }

    /** Folia-safe: run {@code work} on each chunk's owning region, sequentially. */
    private CompletableFuture<Void> forEachChunk(
            World world, int minX, int minZ, int maxX, int maxZ, ChunkWork work) {
        List<int[]> chunks = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int[] c : chunks) {
            final int cx = c[0];
            final int cz = c[1];
            chain = chain.thenCompose(ignored -> regionRun(world, cx << 4, cz << 4, () -> {
                world.getChunkAt(cx, cz);
                work.run(cx, cz);
            }));
        }
        return chain;
    }

    private CompletableFuture<Void> regionRun(World world, int blockX, int blockZ, Runnable work) {
        CompletableFuture<Void> step = new CompletableFuture<>();
        YapSched.region(plugin, world, blockX, blockZ, () -> {
            try {
                work.run();
                step.complete(null);
            } catch (Throwable t) {
                step.completeExceptionally(t);
            }
        });
        return step;
    }

    @FunctionalInterface
    private interface ChunkWork {
        void run(int chunkX, int chunkZ);
    }

    private void layFoundationInChunk(
            World world, RoomGraphBuilder.Layout layout, ThemeTable.Theme theme, int cx, int cz) {
        int y = layout.originY();
        int minX = layout.minX() - 2;
        int minZ = layout.minZ() - 2;
        int maxX = layout.maxX() + 2;
        int maxZ = layout.maxZ() + 2;
        int x0 = Math.max(minX, cx << 4);
        int z0 = Math.max(minZ, cz << 4);
        int x1 = Math.min(maxX, (cx << 4) + 16);
        int z1 = Math.min(maxZ, (cz << 4) + 16);
        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                world.getBlockAt(x, y - 1, z).setType(Material.BEDROCK, false);
                if (x == minX || z == minZ || x == maxX - 1 || z == maxZ - 1) {
                    world.getBlockAt(x, y, z).setType(theme.wall(), false);
                }
            }
        }
    }

    private void carveRoomShellInChunk(
            World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme, int cx, int cz) {
        int x0 = room.x();
        int z0 = room.z();
        int x1 = x0 + room.sizeX();
        int z1 = z0 + room.sizeZ();
        int floor = y;
        int ceil = y + 5;
        int bx0 = Math.max(x0, cx << 4);
        int bz0 = Math.max(z0, cz << 4);
        int bx1 = Math.min(x1, (cx << 4) + 16);
        int bz1 = Math.min(z1, (cz << 4) + 16);
        for (int x = bx0; x < bx1; x++) {
            for (int z = bz0; z < bz1; z++) {
                for (int yy = floor; yy <= ceil; yy++) {
                    Block b = world.getBlockAt(x, yy, z);
                    boolean edge = x == x0 || z == z0 || x == x1 - 1 || z == z1 - 1;
                    boolean corner = (x == x0 || x == x1 - 1) && (z == z0 || z == z1 - 1);
                    if (yy == floor) {
                        b.setType(theme.floor(), false);
                    } else if (yy == ceil) {
                        b.setType(corner ? theme.accent() : theme.wall(), false);
                    } else if (edge) {
                        b.setType(corner ? theme.accent() : theme.wall(), false);
                    } else {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void carveCorridorInChunk(
            World world, RoomGraphBuilder.Room a, RoomGraphBuilder.Room b, int y,
            ThemeTable.Theme theme, int cx, int cz) {
        int ax = a.centerX();
        int az = a.centerZ();
        int bx = b.centerX();
        int bz = b.centerZ();
        int x = ax;
        int z = az;
        while (x != bx) {
            digHallInChunk(world, x, y, z, theme, cx, cz);
            x += Integer.compare(bx, x);
        }
        while (z != bz) {
            digHallInChunk(world, x, y, z, theme, cx, cz);
            z += Integer.compare(bz, z);
        }
        // Final cell at destination center
        digHallInChunk(world, bx, y, bz, theme, cx, cz);
    }

    /**
     * 2×2 walkable tunnel. Always punches air through room walls — never places
     * side-walls on the path (that used to reseal doorways and force mining).
     * Never destroys chests / barrels (re-punch after decor used to wipe loot).
     */
    private void digHallInChunk(World world, int x, int y, int z, ThemeTable.Theme theme, int cx, int cz) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int wx = x + dx;
                int wz = z + dz;
                if ((wx >> 4) != cx || (wz >> 4) != cz) {
                    continue;
                }
                world.getBlockAt(wx, y - 1, wz).setType(Material.BEDROCK, false);
                Block floor = world.getBlockAt(wx, y, wz);
                if (!isLootContainer(floor.getType())) {
                    floor.setType(theme.floor(), false);
                }
                for (int yy = y + 1; yy <= y + 3; yy++) {
                    Block b = world.getBlockAt(wx, yy, wz);
                    if (isLootContainer(b.getType())) {
                        continue;
                    }
                    if (!b.getType().isAir()) {
                        b.setType(Material.AIR, false);
                    }
                }
                Block ceil = world.getBlockAt(wx, y + 4, wz);
                if (!isLootContainer(ceil.getType())) {
                    ceil.setType(theme.wall(), false);
                }
            }
        }
        if ((x >> 4) == cx && (z >> 4) == cz && ((x + z) & 7) == 0) {
            Block lightAt = world.getBlockAt(x, y + 3, z);
            if (!isLootContainer(lightAt.getType())) {
                placeCorridorLight(world, x, y + 3, z);
            }
        }
    }

    private static boolean isLootContainer(Material t) {
        return t == Material.CHEST
                || t == Material.TRAPPED_CHEST
                || t == Material.BARREL
                || t == Material.ENDER_CHEST
                || t == Material.CRAFTING_TABLE
                || t == Material.FURNACE
                || t == Material.BLAST_FURNACE
                || t == Material.SMOKER;
    }

    private static void placeCorridorLight(World world, int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.LIGHT, false);
        if (b.getBlockData() instanceof org.bukkit.block.data.type.Light light) {
            light.setLevel(12);
            b.setBlockData(light, false);
        }
    }

    private void digHall(World world, int x, int y, int z, ThemeTable.Theme theme) {
        digHallInChunk(world, x, y, z, theme, x >> 4, z >> 4);
    }

    private void spawnMobs(
            World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme,
            DifficultyTable.LevelDiff diff, Random rng, String runId) {
        int count = diff.mobsPerRoom();
        int minX = room.x() + 3;
        int minZ = room.z() + 3;
        int spanX = Math.max(1, room.sizeX() - 6);
        int spanZ = Math.max(1, room.sizeZ() - 6);
        for (int i = 0; i < count; i++) {
            EntityType type = theme.mobs().get(rng.nextInt(theme.mobs().size()));
            int bx = minX + rng.nextInt(spanX);
            int bz = minZ + rng.nextInt(spanZ);
            LivingEntity entity = spawnPinned(world, bx, y, bz, type);
            if (entity == null) {
                continue;
            }
            scale(entity, diff, rng.nextDouble() < diff.eliteChance());
            tag(entity, MOB_PDC_KEY, runId);
        }
    }

    private void spawnBoss(
            World world, Location loc, ThemeTable.Theme theme, DifficultyTable.LevelDiff diff, String runId) {
        int bx = loc.getBlockX();
        int by = loc.getBlockY() - 1; // floor under feet
        int bz = loc.getBlockZ();
        // Prefer room floor Y if loc feet are already at originY+1
        if (by < RoomGraphBuilder.ROOM_Y - 1) {
            by = RoomGraphBuilder.ROOM_Y;
        }
        LivingEntity boss = spawnPinned(world, bx, by, bz, theme.boss());
        if (boss == null) {
            plugin.getLogger().warning("Failed to spawn dungeon boss " + theme.boss() + " at "
                    + bx + "," + by + "," + bz);
            return;
        }
        boss.customName(net.kyori.adventure.text.Component.text("Dungeon Boss"));
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);
        var hp = boss.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(diff.bossMaxHealth());
            boss.setHealth(diff.bossMaxHealth());
        }
        var dmg = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(dmg.getBaseValue() * diff.damageMult() * 1.5);
        }
        tag(boss, BOSS_PDC_KEY, runId);
        tag(boss, MOB_PDC_KEY, runId);
        plugin.getLogger().info("Spawned dungeon boss " + theme.boss() + " at "
                + boss.getLocation().getBlockX() + "," + boss.getLocation().getBlockY()
                + "," + boss.getLocation().getBlockZ()
                + " run=" + runId);
    }

    /**
     * Pin mobs to the dungeon floor. Flat worlds have ground at ~-60; vanilla/Paper
     * "safe spawn" will drop entities there if the room column isn't solid underfoot.
     */
    private LivingEntity spawnPinned(World world, int blockX, int floorY, int blockZ, EntityType type) {
        world.getBlockAt(blockX, floorY - 1, blockZ).setType(Material.BEDROCK, false);
        world.getBlockAt(blockX, floorY, blockZ).setType(Material.STONE_BRICKS, false);
        for (int dy = 1; dy <= 3; dy++) {
            Block air = world.getBlockAt(blockX, floorY + dy, blockZ);
            if (!isLootContainer(air.getType())) {
                air.setType(Material.AIR, false);
            }
        }
        Location feet = new Location(world, blockX + 0.5, floorY + 1.0, blockZ + 0.5);
        org.bukkit.entity.Entity raw;
        try {
            raw = world.spawnEntity(feet, type, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "spawnEntity " + type, t);
            return null;
        }
        if (!(raw instanceof LivingEntity entity)) {
            raw.remove();
            return null;
        }
        // Folia forbids Entity#teleport on region threads — spawn at feet already.
        // If something shoved the mob onto the flat surface (~-60), remove and retry once.
        entity.setFallDistance(0f);
        entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        if (entity.getLocation().getY() < floorY) {
            entity.remove();
            try {
                raw = world.spawnEntity(feet, type, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "spawnEntity retry " + type, t);
                return null;
            }
            if (!(raw instanceof LivingEntity retry)) {
                raw.remove();
                return null;
            }
            entity = retry;
            entity.setFallDistance(0f);
            entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            entity.setRemoveWhenFarAway(false);
            entity.setPersistent(true);
            if (entity.getLocation().getY() < floorY) {
                plugin.getLogger().warning("Mob " + type + " still below dungeon floor at y="
                        + entity.getLocation().getY() + " (want ≥" + floorY + ")");
                entity.remove();
                return null;
            }
        }
        return entity;
    }

    private void scale(LivingEntity entity, DifficultyTable.LevelDiff diff, boolean elite) {
        var hp = entity.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            double mult = diff.hpMult() * (elite ? 1.75 : 1.0);
            hp.setBaseValue(hp.getBaseValue() * mult);
            entity.setHealth(hp.getBaseValue());
        }
        var dmg = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(dmg.getBaseValue() * diff.damageMult() * (elite ? 1.4 : 1.0));
        }
        if (elite) {
            entity.customName(net.kyori.adventure.text.Component.text("Elite"));
            entity.setCustomNameVisible(true);
        }
    }

    private void tag(LivingEntity entity, String key, String runId) {
        entity.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, key), PersistentDataType.STRING, runId);
    }

    public void notifyPlayers(List<Player> players, String msg) {
        for (Player p : players) {
            YapSched.entity(plugin, p, () -> p.sendMessage("§6[Dungeon] §7" + msg));
        }
    }
}

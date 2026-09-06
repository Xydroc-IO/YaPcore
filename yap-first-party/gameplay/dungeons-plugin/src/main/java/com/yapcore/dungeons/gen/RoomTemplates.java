package com.yapcore.dungeons.gen;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;

/** Per-room interior templates and doorway framing. */
public final class RoomTemplates {

    private final JavaPlugin plugin;

    public RoomTemplates(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void decorate(
            World world,
            RoomGraphBuilder.Room room,
            int y,
            ThemeTable.Theme theme,
            Random rng,
            String runId,
            List<org.bukkit.Location> chests) {
        switch (room.kind()) {
            case ENTRANCE -> entrance(world, room, y, theme);
            case COMBAT -> combat(world, room, y, theme, rng);
            case TREASURE -> treasure(world, room, y, theme, rng, runId, chests);
            case TRAP -> trap(world, room, y, theme, rng);
            case BOSS -> boss(world, room, y, theme, rng, runId, chests);
        }
        lights(world, room, y, theme);
        floorOre(world, room, y, theme, rng);
    }

    /** Punch a 2×3 doorway on the room wall facing toward (tx, tz) and frame it. */
    public void carveDoorway(World world, RoomGraphBuilder.Room room, int y, int tx, int tz, ThemeTable.Theme theme) {
        int cx = room.centerX();
        int cz = room.centerZ();
        int dx = Integer.compare(tx, cx);
        int dz = Integer.compare(tz, cz);
        // Prefer the dominant axis
        if (Math.abs(tx - cx) >= Math.abs(tz - cz)) {
            dz = 0;
            dx = dx == 0 ? 1 : dx;
        } else {
            dx = 0;
            dz = dz == 0 ? 1 : dz;
        }
        int doorX;
        int doorZ;
        if (dx > 0) {
            doorX = room.x() + room.sizeX() - 1;
            doorZ = cz;
        } else if (dx < 0) {
            doorX = room.x();
            doorZ = cz;
        } else if (dz > 0) {
            doorX = cx;
            doorZ = room.z() + room.sizeZ() - 1;
        } else {
            doorX = cx;
            doorZ = room.z();
        }
        // 2-wide opening
        int ox = dz != 0 ? 1 : 0;
        int oz = dx != 0 ? 1 : 0;
        for (int i = 0; i < 2; i++) {
            int px = doorX + ox * i;
            int pz = doorZ + oz * i;
            for (int h = 1; h <= 3; h++) {
                world.getBlockAt(px, y + h, pz).setType(Material.AIR, false);
            }
            // Frame sides
            world.getBlockAt(px, y + 4, pz).setType(theme.accent(), false);
        }
        // Side pillars of doorway
        if (ox != 0) {
            world.getBlockAt(doorX - 1, y + 1, doorZ).setType(theme.accent(), false);
            world.getBlockAt(doorX - 1, y + 2, doorZ).setType(theme.accent(), false);
            world.getBlockAt(doorX + 2, y + 1, doorZ).setType(theme.accent(), false);
            world.getBlockAt(doorX + 2, y + 2, doorZ).setType(theme.accent(), false);
        } else {
            world.getBlockAt(doorX, y + 1, doorZ - 1).setType(theme.accent(), false);
            world.getBlockAt(doorX, y + 2, doorZ - 1).setType(theme.accent(), false);
            world.getBlockAt(doorX, y + 1, doorZ + 2).setType(theme.accent(), false);
            world.getBlockAt(doorX, y + 2, doorZ + 2).setType(theme.accent(), false);
        }
    }

    private void entrance(World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme) {
        int cx = room.centerX();
        int cz = room.centerZ();
        // Spawn pad
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + dx, y, cz + dz).setType(theme.accent(), false);
            }
        }
        // Welcome pillars
        pillar(world, room.x() + 2, y, room.z() + 2, theme, 3);
        pillar(world, room.x() + room.sizeX() - 3, y, room.z() + 2, theme, 3);
        pillar(world, room.x() + 2, y, room.z() + room.sizeZ() - 3, theme, 3);
        pillar(world, room.x() + room.sizeX() - 3, y, room.z() + room.sizeZ() - 3, theme, 3);
        // Wall alcove shelves
        world.getBlockAt(cx, y + 2, room.z() + 1).setType(Material.CRAFTING_TABLE, false);
        world.getBlockAt(cx + 1, y + 2, room.z() + 1).setType(Material.BARREL, false);
    }

    private void combat(World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme, Random rng) {
        // Four corner pillars
        pillar(world, room.x() + 2, y, room.z() + 2, theme, 4);
        pillar(world, room.x() + room.sizeX() - 3, y, room.z() + 2, theme, 4);
        pillar(world, room.x() + 2, y, room.z() + room.sizeZ() - 3, theme, 4);
        pillar(world, room.x() + room.sizeX() - 3, y, room.z() + room.sizeZ() - 3, theme, 4);
        // Cover blocks / low walls
        int cx = room.centerX();
        int cz = room.centerZ();
        for (int i = -2; i <= 2; i++) {
            if (i == 0) {
                continue;
            }
            world.getBlockAt(cx + i, y + 1, cz).setType(theme.accent(), false);
        }
        // Barricade barrels
        world.getBlockAt(room.x() + 3, y + 1, room.z() + 3).setType(Material.BARREL, false);
        world.getBlockAt(room.x() + room.sizeX() - 4, y + 1, room.z() + room.sizeZ() - 4)
                .setType(Material.BARREL, false);
        if (rng.nextBoolean()) {
            world.getBlockAt(cx, y + 1, cz + 2).setType(Material.COBWEB, false);
        }
    }

    private void treasure(
            World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme,
            Random rng, String runId, List<org.bukkit.Location> chests) {
        int cx = room.centerX();
        int cz = room.centerZ();
        // Pedestal
        world.getBlockAt(cx, y, cz).setType(theme.accent(), false);
        world.getBlockAt(cx, y + 1, cz).setType(Material.CHEST, false);
        tagChest(world.getBlockAt(cx, y + 1, cz), runId);
        chests.add(world.getBlockAt(cx, y + 1, cz).getLocation());
        // Iron bar fence around (opening on south)
        for (int dx = -2; dx <= 2; dx++) {
            world.getBlockAt(cx + dx, y + 1, cz - 2).setType(Material.IRON_BARS, false);
            world.getBlockAt(cx + dx, y + 1, cz + 2).setType(Material.IRON_BARS, false);
        }
        for (int dz = -1; dz <= 1; dz++) {
            world.getBlockAt(cx - 2, y + 1, cz + dz).setType(Material.IRON_BARS, false);
            world.getBlockAt(cx + 2, y + 1, cz + dz).setType(Material.IRON_BARS, false);
        }
        world.getBlockAt(cx, y + 1, cz + 2).setType(Material.AIR, false); // gate
        // Side loot
        if (rng.nextBoolean()) {
            world.getBlockAt(cx - 3, y + 1, cz).setType(Material.BARREL, false);
        }
        pillar(world, room.x() + 2, y, room.z() + 2, theme, 3);
        pillar(world, room.x() + room.sizeX() - 3, y, room.z() + room.sizeZ() - 3, theme, 3);
    }

    private void trap(World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme, Random rng) {
        int cx = room.centerX();
        int cz = room.centerZ();
        // Magma checkerboard in center
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (((dx + dz) & 1) == 0) {
                    world.getBlockAt(cx + dx, y, cz + dz).setType(Material.MAGMA_BLOCK, false);
                } else {
                    world.getBlockAt(cx + dx, y, cz + dz).setType(theme.floor(), false);
                }
            }
        }
        // Fake safe path edges with soul sand / gravel for tension
        Material hazard = theme.wall() == Material.BLACKSTONE || theme.wall() == Material.POLISHED_BLACKSTONE
                ? Material.SOUL_SAND
                : Material.GRAVEL;
        world.getBlockAt(cx - 3, y, cz).setType(hazard, false);
        world.getBlockAt(cx + 3, y, cz).setType(hazard, false);
        // Ceiling drip / danger markers
        world.getBlockAt(cx, y + 5, cz).setType(Material.POINTED_DRIPSTONE, false);
        if (rng.nextBoolean()) {
            world.getBlockAt(cx + 1, y + 1, cz + 1).setType(Material.COBWEB, false);
            world.getBlockAt(cx - 1, y + 1, cz - 1).setType(Material.COBWEB, false);
        }
    }

    private void boss(
            World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme,
            Random rng, String runId, List<org.bukkit.Location> chests) {
        int cx = room.centerX();
        int cz = room.centerZ();
        // Raised dais 5x5
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(cx + dx, y, cz + dz).setType(theme.accent(), false);
                world.getBlockAt(cx + dx, y + 1, cz + dz).setType(theme.accent(), false);
            }
        }
        // Stairs up onto dais from south
        for (int dx = -1; dx <= 1; dx++) {
            world.getBlockAt(cx + dx, y + 1, cz + 3).setType(Material.AIR, false);
            world.getBlockAt(cx + dx, y, cz + 3).setType(theme.floor(), false);
            world.getBlockAt(cx + dx, y + 1, cz + 2).setType(Material.AIR, false);
        }
        // Throne
        world.getBlockAt(cx, y + 2, cz - 1).setType(theme.accent(), false);
        world.getBlockAt(cx, y + 3, cz - 1).setType(theme.accent(), false);
        world.getBlockAt(cx - 1, y + 2, cz - 1).setType(theme.wall(), false);
        world.getBlockAt(cx + 1, y + 2, cz - 1).setType(theme.wall(), false);
        // Corner pillars
        pillar(world, room.x() + 3, y, room.z() + 3, theme, 5);
        pillar(world, room.x() + room.sizeX() - 4, y, room.z() + 3, theme, 5);
        pillar(world, room.x() + 3, y, room.z() + room.sizeZ() - 4, theme, 5);
        pillar(world, room.x() + room.sizeX() - 4, y, room.z() + room.sizeZ() - 4, theme, 5);
        // Loot chest behind throne
        world.getBlockAt(cx, y + 2, cz - 2).setType(Material.CHEST, false);
        tagChest(world.getBlockAt(cx, y + 2, cz - 2), runId);
        chests.add(world.getBlockAt(cx, y + 2, cz - 2).getLocation());
        // Ring lights on dais corners
        world.getBlockAt(cx - 2, y + 2, cz - 2).setType(theme.light(), false);
        world.getBlockAt(cx + 2, y + 2, cz - 2).setType(theme.light(), false);
        world.getBlockAt(cx - 2, y + 2, cz + 2).setType(theme.light(), false);
        world.getBlockAt(cx + 2, y + 2, cz + 2).setType(theme.light(), false);
    }

    private void lights(World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme) {
        int cx = room.centerX();
        int cz = room.centerZ();
        world.getBlockAt(cx, y + 5, cz).setType(theme.light(), false);
        world.getBlockAt(room.x() + 2, y + 3, room.z() + 2).setType(theme.light(), false);
        world.getBlockAt(room.x() + room.sizeX() - 3, y + 3, room.z() + room.sizeZ() - 3)
                .setType(theme.light(), false);
    }

    private void floorOre(World world, RoomGraphBuilder.Room room, int y, ThemeTable.Theme theme, Random rng) {
        int n = 2 + rng.nextInt(4);
        for (int i = 0; i < n; i++) {
            int ox = room.x() + 2 + rng.nextInt(Math.max(1, room.sizeX() - 4));
            int oz = room.z() + 2 + rng.nextInt(Math.max(1, room.sizeZ() - 4));
            Block b = world.getBlockAt(ox, y, oz);
            if (b.getType() == theme.floor()) {
                b.setType(theme.ore(), false);
            }
        }
    }

    private static void pillar(World world, int x, int y, int z, ThemeTable.Theme theme, int height) {
        for (int h = 1; h <= height; h++) {
            world.getBlockAt(x, y + h, z).setType(theme.accent(), false);
        }
        world.getBlockAt(x, y + height, z).setType(theme.light(), false);
    }

    private void tagChest(Block block, String runId) {
        if (block.getState() instanceof Chest chest) {
            chest.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "yap_dungeon_chest"),
                    PersistentDataType.STRING, runId);
            chest.update();
        }
    }
}

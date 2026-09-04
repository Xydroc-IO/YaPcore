package com.yapcore.bench;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * Shared bot spread + per-home redstone fixtures (matches {@code swarm.js} grid).
 * 4 quadrants × 8 cells = 32 homes — keeps 250 bots off origin-border planes.
 */
final class BenchSpreadGrid {

    private static final int BASE = 64;
    private static final int STEP = 16;
    private static final int[][] QUAD_SIGN = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
    private static final int[][] CELLS = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {2, 1}, {0, 2}, {1, 2}};

    private static final int[][] HOMES = buildHomes();

    private BenchSpreadGrid() {
    }

    static int[][] homes() {
        return HOMES;
    }

    static int[] homeForBotId(int botId) {
        return HOMES[Math.floorMod(botId, HOMES.length)];
    }

    static void placeRedstoneAtHomes(World world) {
        for (int[] xz : HOMES) {
            int bx = xz[0];
            int bz = xz[1];
            int cx = bx >> 4;
            int cz = bz >> 4;
            world.getChunkAt(cx, cz).load(true);
            world.setChunkForceLoaded(cx, cz, true);
            int y = Math.max(world.getHighestBlockYAt(bx, bz), 64) + 2;
            // Observer clock + lamp (always-on tile tick)
            world.getBlockAt(bx, y, bz).setType(Material.OBSERVER);
            world.getBlockAt(bx, y + 1, bz).setType(Material.OBSERVER);
            world.getBlockAt(bx + 1, y, bz).setType(Material.REDSTONE_LAMP);
            world.getBlockAt(bx + 1, y + 1, bz).setType(Material.REDSTONE_BLOCK);
            // Hopper line (inventory + tile entity stress near bots)
            world.getBlockAt(bx - 1, y, bz).setType(Material.STONE);
            world.getBlockAt(bx - 1, y + 1, bz).setType(Material.HOPPER);
            world.getBlockAt(bx, y, bz + 1).setType(Material.CHEST);
        }
    }

    private static int[][] buildHomes() {
        int[][] out = new int[QUAD_SIGN.length * CELLS.length][2];
        int i = 0;
        for (int[] sign : QUAD_SIGN) {
            for (int[] cell : CELLS) {
                out[i][0] = sign[0] * (BASE + cell[0] * STEP);
                out[i][1] = sign[1] * (BASE + cell[1] * STEP);
                i++;
            }
        }
        return out;
    }
}

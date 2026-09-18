package com.yapcore.map;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

/**
 * Capture loaded chunks on the owning region thread. Never call
 * {@code World#getBlockAt} / {@code Block#getType} for map scans — those
 * {@code syncLoad} neighboring/unloaded chunks and park the Folia tick.
 */
final class ChunkSnapshots {

    private ChunkSnapshots() {
    }

    static ChunkSnapshot captureIfLoaded(World world, int chunkX, int chunkZ, boolean biomes) {
        if (world == null) {
            return null;
        }
        try {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                // Owning region thread only. Loads this chunk, not its neighbors.
                world.getChunkAt(chunkX, chunkZ);
            }
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                return null;
            }
            return world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, biomes, false);
        } catch (Throwable t) {
            return null;
        }
    }

    static Material blockType(ChunkSnapshot snap, int lx, int y, int lz) {
        try {
            Material type = snap.getBlockType(lx, y, lz);
            return type == null ? Material.AIR : type;
        } catch (Throwable t) {
            return Material.AIR;
        }
    }

    static BlockData blockData(ChunkSnapshot snap, int lx, int y, int lz) {
        try {
            return snap.getBlockData(lx, y, lz);
        } catch (Throwable t) {
            return null;
        }
    }

    static Biome biome(ChunkSnapshot snap, int lx, int y, int lz) {
        try {
            return snap.getBiome(lx, y, lz);
        } catch (Throwable t) {
            return null;
        }
    }

    static int highestSolidY(ChunkSnapshot snap, int lx, int lz, int minY, int maxY) {
        int top = Math.max(minY, maxY);
        for (int y = top; y >= minY; y--) {
            Material type = blockType(snap, lx, y, lz);
            if (type.isAir() || !type.isSolid()) {
                continue;
            }
            return y;
        }
        return minY;
    }
}

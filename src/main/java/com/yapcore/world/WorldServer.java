package com.yapcore.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * YapEngine-owned world: in-memory chunk store + spawn. No Mojang kernel.
 */
public final class WorldServer {

    private static final WorldServer OVERWORLD = new WorldServer("minecraft:overworld");

    private final String name;
    private final ConcurrentMap<Long, ChunkColumn> chunks = new ConcurrentHashMap<>();
    private final double spawnX = 8.5;
    private final double spawnY = 65.0;
    private final double spawnZ = -8.5;

    private WorldServer(String name) {
        this.name = name;
    }

    public static WorldServer overworld() {
        return OVERWORLD;
    }

    public String name() {
        return name;
    }

    public double spawnX() {
        return spawnX;
    }

    public double spawnY() {
        return spawnY;
    }

    public double spawnZ() {
        return spawnZ;
    }

    public ChunkColumn getOrCreateChunk(int chunkX, int chunkZ) {
        long key = pack(chunkX, chunkZ);
        return chunks.computeIfAbsent(key, k -> FlatWorldGenerator.generate(chunkX, chunkZ));
    }

    public ChunkColumn getChunkIfLoaded(int chunkX, int chunkZ) {
        return chunks.get(pack(chunkX, chunkZ));
    }

    public int getBlock(int x, int y, int z) {
        ChunkColumn col = getOrCreateChunk(x >> 4, z >> 4);
        return col.getBlock(x, y, z);
    }

    public void setBlock(int x, int y, int z, int state) {
        ChunkColumn col = getOrCreateChunk(x >> 4, z >> 4);
        col.setBlock(x, y, z, state);
    }

    private static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}

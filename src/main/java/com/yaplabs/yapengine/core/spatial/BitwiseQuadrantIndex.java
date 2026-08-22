package com.yaplabs.yapengine.core.spatial;

/**
 * Bitwise quadrant index — CPU evaluates spatial thread alignment in &lt;1 ns
 * for the four cardinals. Optional spawn box is a single bounds check first.
 * <p>
 * Layout of packed chunk key {@code long}:
 * <pre>
 *   bits 63..32 → chunkX (signed int)
 *   bits 31..0  → chunkZ (signed int)
 * </pre>
 * Cardinal id (0..3) from sign bits; {@link SpatialQuadrant#SPAWN} id = 4.
 */
public final class BitwiseQuadrantIndex {

    private BitwiseQuadrantIndex() {
    }

    /** Pack chunk coordinates into a primitive long key. */
    public static long packChunk(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int unpackChunkX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackChunkZ(long packed) {
        return (int) packed;
    }

    public static long packBlock(int blockX, int blockZ) {
        return packChunk(blockX >> 4, blockZ >> 4);
    }

    /**
     * Quadrant id from packed chunk key.
     * Spawn box (if enabled) wins; else bit0=east (chunkX≥0), bit1=south (chunkZ≥0).
     * 0=NW 1=NE 2=SW 3=SE 4=SPAWN
     */
    public static int quadrantId(long packedChunk) {
        int chunkX = (int) (packedChunk >> 32);
        int chunkZ = (int) packedChunk;
        return quadrantId(chunkX, chunkZ);
    }

    public static int quadrantId(int chunkX, int chunkZ) {
        if (SpatialSpawnRegion.containsChunk(chunkX, chunkZ)) {
            return SpatialQuadrant.SPAWN.id();
        }
        int east = (~chunkX) >>> 31;   // 1 if chunkX >= 0
        int south = (~chunkZ) >>> 31;  // 1 if chunkZ >= 0
        return east | (south << 1);
    }

    public static int quadrantIdFromBlock(int blockX, int blockZ) {
        return quadrantId(blockX >> 4, blockZ >> 4);
    }

    public static SpatialQuadrant toQuadrant(int id) {
        return SpatialQuadrant.byId(id);
    }

    public static SpatialQuadrant fromBlock(int blockX, int blockZ) {
        return toQuadrant(quadrantIdFromBlock(blockX, blockZ));
    }

    public static SpatialQuadrant fromChunk(int chunkX, int chunkZ) {
        return toQuadrant(quadrantId(chunkX, chunkZ));
    }

    public static SpatialQuadrant fromPackedChunk(long packedChunk) {
        return toQuadrant(quadrantId(packedChunk));
    }
}

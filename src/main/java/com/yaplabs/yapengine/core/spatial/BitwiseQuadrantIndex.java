package com.yaplabs.yapengine.core.spatial;

/**
 * Bitwise quadrant index — CPU evaluates spatial thread alignment in &lt;1 ns.
 * Layout of packed chunk key {@code long}:
 * <pre>
 *   bits 63..32 → chunkX (signed int)
 *   bits 31..0  → chunkZ (signed int)
 * </pre>
 * Quadrant id (0..3) from sign bits only — no strings, no HashMap lookups.
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
     * Quadrant from packed chunk key using only bitwise ops:
     * bit0 = east (chunkX &gt;= 0), bit1 = south (chunkZ &gt;= 0).
     * 0=NW 1=NE 2=SW 3=SE
     */
    public static int quadrantId(long packedChunk) {
        int chunkX = (int) (packedChunk >> 32);
        int chunkZ = (int) packedChunk;
        int east = (~chunkX) >>> 31;   // 1 if chunkX >= 0
        int south = (~chunkZ) >>> 31;  // 1 if chunkZ >= 0
        return east | (south << 1);
    }

    public static int quadrantIdFromBlock(int blockX, int blockZ) {
        return quadrantId(packBlock(blockX, blockZ));
    }

    public static SpatialQuadrant toQuadrant(int id) {
        return SpatialQuadrant.byId(id & 3);
    }

    public static SpatialQuadrant fromBlock(int blockX, int blockZ) {
        return toQuadrant(quadrantIdFromBlock(blockX, blockZ));
    }

    public static SpatialQuadrant fromPackedChunk(long packedChunk) {
        return toQuadrant(quadrantId(packedChunk));
    }
}

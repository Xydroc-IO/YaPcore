package com.yapcore.map;

import java.util.Arrays;

/**
 * Compact mesh for one chunk.
 *
 * <ul>
 *   <li>v1 (legacy): {@code [localX, y, localZ, rgb, …]} stride 4 — one unit cube per solid</li>
 *   <li>v2: {@code [localX, y, localZ, sx, sy, sz, rgb, …]} stride 7 — greedy merged boxes</li>
 * </ul>
 * {@code localX}/{@code localZ} are 0–15; {@code y} is world Y; sizes are ≥1; {@code rgb} is 0xRRGGBB.
 */
public final class ChunkMeshData {

    public static final int FORMAT_V1 = 1;
    public static final int FORMAT_V2 = 2;
    public static final int STRIDE_V1 = 4;
    public static final int STRIDE_V2 = 7;

    private final int chunkX;
    private final int chunkZ;
    private final int formatVersion;
    private final int[] packed;

    /** Legacy v1 constructor (unit voxels). */
    public ChunkMeshData(int chunkX, int chunkZ, int[] packed) {
        this(chunkX, chunkZ, FORMAT_V1, packed);
    }

    public ChunkMeshData(int chunkX, int chunkZ, int formatVersion, int[] packed) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.formatVersion = formatVersion <= 0 ? FORMAT_V1 : formatVersion;
        this.packed = packed == null ? new int[0] : packed;
    }

    public static ChunkMeshData boxes(int chunkX, int chunkZ, int[] packed) {
        return new ChunkMeshData(chunkX, chunkZ, FORMAT_V2, packed);
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public int formatVersion() {
        return formatVersion;
    }

    public int stride() {
        return formatVersion >= FORMAT_V2 ? STRIDE_V2 : STRIDE_V1;
    }

    /** Flat array length; multiple of {@link #stride()}. */
    public int packedLength() {
        return packed.length;
    }

    /** Number of primitives (v1 voxels or v2 boxes). */
    public int blockCount() {
        int s = stride();
        return s <= 0 ? 0 : packed.length / s;
    }

    public int[] packed() {
        return packed;
    }

    public int[] copyPacked() {
        return Arrays.copyOf(packed, packed.length);
    }
}

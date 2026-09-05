package com.yapcore.map;

import java.util.Arrays;

/**
 * Pure voxel extraction helpers (no Bukkit). Solid cells become unit-cube instances;
 * world sampling lives in {@link ChunkMeshRenderer}.
 */
public final class ChunkMeshExtractor {

    public static final int CHUNK_SIZE = 16;

    private ChunkMeshExtractor() {
    }

    /**
     * Extract solid voxels from a dense column buffer.
     *
     * @param rgbByLocalXyz indexed {@code [localX][yIndex][localZ]}; value {@code < 0} means air/non-solid
     * @param minY          world Y corresponding to {@code yIndex == 0}
     * @return packed {@code [lx, worldY, lz, rgb, …]}
     */
    public static ChunkMeshData extract(int chunkX, int chunkZ, int[][][] rgbByLocalXyz, int minY) {
        if (rgbByLocalXyz == null) {
            return new ChunkMeshData(chunkX, chunkZ, new int[0]);
        }
        int height = 0;
        for (int x = 0; x < CHUNK_SIZE; x++) {
            if (rgbByLocalXyz[x] != null) {
                height = Math.max(height, rgbByLocalXyz[x].length);
            }
        }
        int capacity = CHUNK_SIZE * CHUNK_SIZE * Math.max(1, height) * 4;
        int[] buf = new int[capacity];
        int n = 0;
        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
            int[][] columnPlane = rgbByLocalXyz[lx];
            if (columnPlane == null) {
                continue;
            }
            for (int yIndex = 0; yIndex < columnPlane.length; yIndex++) {
                int[] row = columnPlane[yIndex];
                if (row == null) {
                    continue;
                }
                for (int lz = 0; lz < CHUNK_SIZE && lz < row.length; lz++) {
                    int rgb = row[lz];
                    if (rgb < 0) {
                        continue;
                    }
                    if (n + 4 > buf.length) {
                        buf = Arrays.copyOf(buf, buf.length * 2);
                    }
                    buf[n++] = lx;
                    buf[n++] = minY + yIndex;
                    buf[n++] = lz;
                    buf[n++] = rgb & 0xffffff;
                }
            }
        }
        return new ChunkMeshData(chunkX, chunkZ, Arrays.copyOf(buf, n));
    }

    /**
     * Append one solid voxel into a growable packed buffer (mutates {@code state}).
     */
    public static void appendVoxel(GrowablePacked state, int localX, int worldY, int localZ, int rgb) {
        if (state == null) {
            return;
        }
        state.append(localX, worldY, localZ, rgb & 0xffffff);
    }

    /** Mutable packed buffer used while scanning a live chunk. */
    public static final class GrowablePacked {
        private int[] buf;
        private int size;

        public GrowablePacked(int initialCapacityVoxels) {
            int cap = Math.max(16, initialCapacityVoxels) * 4;
            this.buf = new int[cap];
            this.size = 0;
        }

        public void append(int localX, int worldY, int localZ, int rgb) {
            if (size + 4 > buf.length) {
                buf = Arrays.copyOf(buf, buf.length * 2);
            }
            buf[size++] = localX;
            buf[size++] = worldY;
            buf[size++] = localZ;
            buf[size++] = rgb & 0xffffff;
        }

        public ChunkMeshData toData(int chunkX, int chunkZ) {
            return new ChunkMeshData(chunkX, chunkZ, Arrays.copyOf(buf, size));
        }

        public int blockCount() {
            return size / 4;
        }
    }

    /**
     * Cap Y for mesh sampling — mirrors {@link TileRenderer} nether roof / max-height rules.
     *
     * @param environment 0=NORMAL, -1=NETHER, 1=THE_END (avoids Bukkit enum in pure tests)
     */
    public static int columnMaxY(int environment, int worldMaxHeightExclusive, int minHeight,
                                 int configuredMaxY) {
        int worldMax = worldMaxHeightExclusive - 1;
        int cfg = Math.max(minHeight, configuredMaxY);
        if (environment == -1) {
            // Nether roof-aware: stay under bedrock ceiling.
            return Math.min(126, Math.min(worldMax, cfg));
        }
        return Math.min(worldMax, cfg);
    }
}

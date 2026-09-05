package com.yapcore.map;

import java.util.Locale;

/**
 * Shared layer names and pure column-selection helpers for flat tiles and 3D meshes.
 *
 * <p>Cave sampling mirrors {@link TileRenderer}: underground slice ≤ cave-max-y,
 * preferring solids with open space above.
 */
public final class MapLayerSampler {

    public static final String LAYER_SURFACE = "surface";
    public static final String LAYER_CAVE = "cave";
    public static final String LAYER_FULL = "full";

    private MapLayerSampler() {
    }

    public static String normalizeMeshLayer(String layer) {
        if (layer == null || layer.isBlank()) {
            return LAYER_FULL;
        }
        String n = layer.trim().toLowerCase(Locale.ROOT);
        if (LAYER_SURFACE.equals(n) || LAYER_CAVE.equals(n) || LAYER_FULL.equals(n)) {
            return n;
        }
        return LAYER_FULL;
    }

    public static boolean isMeshLayer(String layer) {
        String n = normalizeMeshLayer(layer);
        return LAYER_SURFACE.equals(n) || LAYER_CAVE.equals(n) || LAYER_FULL.equals(n);
    }

    /**
     * From a dense column of RGB values ({@code <0} = air), return world Y values to keep
     * for the given mesh layer. {@code yIndex 0} maps to {@code minY}.
     *
     * @return sorted ascending world Y list (may be empty)
     */
    public static int[] selectWorldYs(int[] columnRgbByYIndex, int minY, String layer,
                                      int caveMaxY, int surfaceMaxY) {
        if (columnRgbByYIndex == null || columnRgbByYIndex.length == 0) {
            return new int[0];
        }
        String mode = normalizeMeshLayer(layer);
        int height = columnRgbByYIndex.length;
        if (LAYER_FULL.equals(mode)) {
            int count = 0;
            for (int rgb : columnRgbByYIndex) {
                if (rgb >= 0) {
                    count++;
                }
            }
            int[] out = new int[count];
            int n = 0;
            for (int i = 0; i < height; i++) {
                if (columnRgbByYIndex[i] >= 0) {
                    out[n++] = minY + i;
                }
            }
            return out;
        }
        if (LAYER_SURFACE.equals(mode)) {
            int top = highestSolidIndex(columnRgbByYIndex, height - 1);
            if (top < 0) {
                return new int[0];
            }
            return new int[] {minY + top};
        }
        // cave
        int surfaceIdx = highestSolidIndex(columnRgbByYIndex, height - 1);
        if (surfaceIdx < 0) {
            return new int[0];
        }
        int surfaceWorldY = minY + surfaceIdx;
        int caveCapWorld = Math.min(surfaceWorldY - 1, caveMaxY);
        int caveCapIdx = caveCapWorld - minY;
        if (caveCapIdx < 0) {
            return new int[] {surfaceWorldY};
        }
        // Prefer solids with air above within the cave window (open caves).
        int count = 0;
        for (int i = 0; i <= caveCapIdx && i < height; i++) {
            if (columnRgbByYIndex[i] < 0) {
                continue;
            }
            boolean openAbove = i + 1 >= height || columnRgbByYIndex[i + 1] < 0;
            if (openAbove) {
                count++;
            }
        }
        if (count == 0) {
            int under = highestSolidIndex(columnRgbByYIndex, Math.min(caveCapIdx, height - 1));
            if (under < 0) {
                return new int[] {surfaceWorldY};
            }
            return new int[] {minY + under};
        }
        int[] out = new int[count];
        int n = 0;
        for (int i = 0; i <= caveCapIdx && i < height; i++) {
            if (columnRgbByYIndex[i] < 0) {
                continue;
            }
            boolean openAbove = i + 1 >= height || columnRgbByYIndex[i + 1] < 0;
            if (openAbove) {
                out[n++] = minY + i;
            }
        }
        return out;
    }

    /**
     * Filter a dense chunk buffer in-place for a mesh layer: cells not selected become air (−1).
     */
    public static void applyLayerFilter(int[][][] rgbByLocalXyz, int minY, String layer,
                                        int caveMaxY) {
        if (rgbByLocalXyz == null) {
            return;
        }
        String mode = normalizeMeshLayer(layer);
        if (LAYER_FULL.equals(mode)) {
            return;
        }
        int sizeX = Math.min(ChunkMeshExtractor.CHUNK_SIZE, rgbByLocalXyz.length);
        for (int lx = 0; lx < sizeX; lx++) {
            if (rgbByLocalXyz[lx] == null) {
                continue;
            }
            int height = rgbByLocalXyz[lx].length;
            int sizeZ = 0;
            for (int y = 0; y < height; y++) {
                if (rgbByLocalXyz[lx][y] != null) {
                    sizeZ = Math.max(sizeZ, Math.min(ChunkMeshExtractor.CHUNK_SIZE, rgbByLocalXyz[lx][y].length));
                }
            }
            for (int lz = 0; lz < sizeZ; lz++) {
                int[] column = new int[height];
                for (int y = 0; y < height; y++) {
                    if (rgbByLocalXyz[lx][y] == null || lz >= rgbByLocalXyz[lx][y].length) {
                        column[y] = -1;
                    } else {
                        column[y] = rgbByLocalXyz[lx][y][lz];
                    }
                }
                int[] keep = selectWorldYs(column, minY, mode, caveMaxY, minY + height - 1);
                boolean[] keepIdx = new boolean[height];
                for (int worldY : keep) {
                    int yi = worldY - minY;
                    if (yi >= 0 && yi < height) {
                        keepIdx[yi] = true;
                    }
                }
                for (int y = 0; y < height; y++) {
                    if (rgbByLocalXyz[lx][y] == null || lz >= rgbByLocalXyz[lx][y].length) {
                        continue;
                    }
                    if (!keepIdx[y]) {
                        rgbByLocalXyz[lx][y][lz] = -1;
                    }
                }
            }
        }
    }

    private static int highestSolidIndex(int[] column, int maxIndexInclusive) {
        int top = Math.min(maxIndexInclusive, column.length - 1);
        for (int i = top; i >= 0; i--) {
            if (column[i] >= 0) {
                return i;
            }
        }
        return -1;
    }
}

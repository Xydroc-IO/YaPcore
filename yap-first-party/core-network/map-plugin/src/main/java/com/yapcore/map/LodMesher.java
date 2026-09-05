package com.yapcore.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Server-side LOD pyramid from LOD0 greedy/model boxes.
 *
 * <ul>
 *   <li>LOD0 — full detail (input)</li>
 *   <li>LOD1 — drop thin features, merge adjacent same-color AABBs</li>
 *   <li>LOD2 — coarser merge + larger thin-feature threshold</li>
 * </ul>
 * Pure logic (no Bukkit). Packed layout is milliblock v2 stride 7.
 */
public final class LodMesher {

    /** Skip boxes thinner than this (milliblocks) at LOD1 — carpets / fence bars. */
    public static final int LOD1_MIN_THICKNESS = MeshUnits.of(0.2f);
    /** Skip boxes thinner than this at LOD2. */
    public static final int LOD2_MIN_THICKNESS = MeshUnits.of(0.45f);

    private LodMesher() {
    }

    public static ChunkMeshData buildLod(ChunkMeshData lod0, int lod) {
        if (lod0 == null) {
            return ChunkMeshData.boxes(0, 0, new int[0]);
        }
        if (lod <= 0) {
            return lod0;
        }
        if (lod == 1) {
            return buildLod1(lod0);
        }
        return buildLod2(lod0);
    }

    public static ChunkMeshData buildLod1(ChunkMeshData lod0) {
        return simplify(lod0, LOD1_MIN_THICKNESS, 1);
    }

    public static ChunkMeshData buildLod2(ChunkMeshData lod0) {
        // First drop thin features at LOD1 threshold, then coarser pass.
        ChunkMeshData mid = simplify(lod0, LOD1_MIN_THICKNESS, 1);
        return simplify(mid, LOD2_MIN_THICKNESS, 2);
    }

    /**
     * Drop thin boxes then greedily merge axis-aligned same-color neighbors.
     *
     * @param quantize quantize origins/sizes to this many milliblock steps (1 = none, 2 = half-block snap)
     */
    static ChunkMeshData simplify(ChunkMeshData src, int minThickness, int quantize) {
        if (src == null || src.blockCount() == 0) {
            return ChunkMeshData.boxes(
                    src == null ? 0 : src.chunkX(),
                    src == null ? 0 : src.chunkZ(),
                    new int[0]);
        }
        int stride = GreedyMesher.STRIDE;
        int[] p = src.packed();
        List<int[]> boxes = new ArrayList<>(src.blockCount());
        for (int i = 0; i + stride - 1 < p.length; i += stride) {
            int sx = p[i + 3];
            int sy = p[i + 4];
            int sz = p[i + 5];
            int minDim = Math.min(sx, Math.min(sy, sz));
            if (minDim < minThickness) {
                continue;
            }
            int[] b = Arrays.copyOfRange(p, i, i + stride);
            if (quantize > 1) {
                quantizeBox(b, quantize * (MeshUnits.UNIT / 2));
            }
            boxes.add(b);
        }
        mergeAdjacent(boxes);
        int[] out = new int[boxes.size() * stride];
        int n = 0;
        for (int[] b : boxes) {
            System.arraycopy(b, 0, out, n, stride);
            n += stride;
        }
        return ChunkMeshData.boxes(src.chunkX(), src.chunkZ(), out);
    }

    /** Snap box to a grid so distant LOD collapses small offsets. */
    static void quantizeBox(int[] b, int step) {
        if (step <= 1) {
            return;
        }
        int x1 = b[0] + b[3];
        int y1 = b[1] + b[4];
        int z1 = b[2] + b[5];
        b[0] = (b[0] / step) * step;
        b[1] = (b[1] / step) * step;
        b[2] = (b[2] / step) * step;
        x1 = ((x1 + step - 1) / step) * step;
        y1 = ((y1 + step - 1) / step) * step;
        z1 = ((z1 + step - 1) / step) * step;
        b[3] = Math.max(step, x1 - b[0]);
        b[4] = Math.max(step, y1 - b[1]);
        b[5] = Math.max(step, z1 - b[2]);
    }

    /**
     * Repeatedly merge pairs that share a face and have equal color + matching extents.
     */
    static void mergeAdjacent(List<int[]> boxes) {
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            boxes.sort(Comparator
                    .comparingInt((int[] b) -> b[6])
                    .thenComparingInt(b -> b[1])
                    .thenComparingInt(b -> b[0])
                    .thenComparingInt(b -> b[2]));
            for (int i = 0; i < boxes.size(); i++) {
                int[] a = boxes.get(i);
                for (int j = i + 1; j < boxes.size(); j++) {
                    int[] b = boxes.get(j);
                    if (a[6] != b[6]) {
                        break; // sorted by color
                    }
                    int[] merged = tryMerge(a, b);
                    if (merged != null) {
                        boxes.set(i, merged);
                        boxes.remove(j);
                        progressed = true;
                        break;
                    }
                }
                if (progressed) {
                    break;
                }
            }
        }
    }

    /** Merge if boxes share a full face and form a larger AABB. */
    static int[] tryMerge(int[] a, int[] b) {
        if (a[6] != b[6]) {
            return null;
        }
        // X-adjacent
        if (a[1] == b[1] && a[4] == b[4] && a[2] == b[2] && a[5] == b[5]) {
            if (a[0] + a[3] == b[0]) {
                return box(a[0], a[1], a[2], a[3] + b[3], a[4], a[5], a[6]);
            }
            if (b[0] + b[3] == a[0]) {
                return box(b[0], a[1], a[2], a[3] + b[3], a[4], a[5], a[6]);
            }
        }
        // Y-adjacent
        if (a[0] == b[0] && a[3] == b[3] && a[2] == b[2] && a[5] == b[5]) {
            if (a[1] + a[4] == b[1]) {
                return box(a[0], a[1], a[2], a[3], a[4] + b[4], a[5], a[6]);
            }
            if (b[1] + b[4] == a[1]) {
                return box(a[0], b[1], a[2], a[3], a[4] + b[4], a[5], a[6]);
            }
        }
        // Z-adjacent
        if (a[0] == b[0] && a[3] == b[3] && a[1] == b[1] && a[4] == b[4]) {
            if (a[2] + a[5] == b[2]) {
                return box(a[0], a[1], a[2], a[3], a[4], a[5] + b[5], a[6]);
            }
            if (b[2] + b[5] == a[2]) {
                return box(a[0], a[1], b[2], a[3], a[4], a[5] + b[5], a[6]);
            }
        }
        return null;
    }

    private static int[] box(int x, int y, int z, int sx, int sy, int sz, int rgb) {
        return new int[] {x, y, z, sx, sy, sz, rgb & 0xffffff};
    }
}

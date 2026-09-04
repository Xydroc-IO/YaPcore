package com.yapcore.bench;

import java.util.LinkedHashSet;
import java.util.Set;

/** Spawn-collapse interest chunk layouts for region load benches. */
final class BenchRegionSpawnChunks {

    private BenchRegionSpawnChunks() {
    }

    /**
     * Spawn-collapse interest: a single Folia region around spawn (chunks in one
     * contiguous 3×3 block) so all load shares one tick runner.
     * <p>With {@code -Dyap.bench.lobes=2}, load is split into west/east lobes separated by a
     * Folia-safe gap so regions can tick in parallel (see Folia empty-section radius).
     */
    static final int[][] SPAWN_COLLAPSE_CHUNKS = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /** Half-lobe 3×3 offsets; applied at ±{@code lobeOffsetChunks}. */
    static final int[][] LOBE_OFFSETS = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /** After spawn, pin lobes only (corridor unforced) — citeable dynamic carve vs contiguous stock. */
    static boolean stripTwoPhase() {
        return Boolean.parseBoolean(System.getProperty("yap.bench.strip_two_phase", "false"));
    }

    /** YaP-only: full contiguous force-load (no lobe gap) for dynamic carve+partition cite. */
    static boolean contiguousCarve() {
        return Boolean.parseBoolean(System.getProperty("yap.bench.contiguous_carve", "false"));
    }

    static int stripHalfWidth() {
        return Integer.getInteger("yap.bench.strip_half_width", 0);
    }

    static int stripZRadius() {
        return Math.max(0, Integer.getInteger("yap.bench.strip_z_radius", 1));
    }

    /** Gap half-width in chunks; lobes are chunks with {@code |cx| > gapHalf}. */
    static int stripGapHalf() {
        int gap = Math.max(0, Integer.getInteger("yap.bench.strip_gap_half", 0));
        if (gap > 0) {
            return gap;
        }
        int half = stripHalfWidth();
        if (half > 0 && stripTwoPhase()) {
            // Folia-safe default: ~25% of strip center empty (matches gap1-style cite).
            return Math.max(16, half / 3);
        }
        return 0;
    }

    /** Full contiguous strip — used to spawn load before corridor unpin (two-phase). */
    static Set<long[]> spawnCollapseFullStripChunks() {
        int stripHalf = stripHalfWidth();
        if (stripHalf <= 0) {
            return Set.of();
        }
        int zRadius = stripZRadius();
        Set<long[]> out = new LinkedHashSet<>();
        for (int cx = -stripHalf; cx <= stripHalf; cx++) {
            for (int cz = -zRadius; cz <= zRadius; cz++) {
                out.add(BenchRegionLoadLoops.pack(cx, cz));
            }
        }
        return out;
    }

    /** Lobe pin set: wide strip minus Folia gap corridor. */
    static Set<long[]> spawnCollapseLobePinChunks() {
        int stripHalf = stripHalfWidth();
        if (stripHalf <= 0) {
            return Set.of();
        }
        int zRadius = stripZRadius();
        int gapHalf = stripGapHalf();
        Set<long[]> out = new LinkedHashSet<>();
        for (int cx = -stripHalf; cx <= stripHalf; cx++) {
            if (gapHalf > 0 && Math.abs(cx) <= gapHalf) {
                continue;
            }
            for (int cz = -zRadius; cz <= zRadius; cz++) {
                out.add(BenchRegionLoadLoops.pack(cx, cz));
            }
        }
        return out;
    }

    static Set<long[]> spawnCollapseChunks() {
        int lobes = Math.max(1, Integer.getInteger("yap.bench.lobes", 1));
        // Contiguous wide strip so corridor carve can unload a Folia-safe middle gap.
        int stripHalf = stripHalfWidth();
        if (stripHalf > 0) {
            if (contiguousCarve()) {
                return spawnCollapseFullStripChunks();
            }
            if (stripTwoPhase()) {
                return spawnCollapseLobePinChunks();
            }
            int zRadius = stripZRadius();
            // Optional pre-carved Folia-safe gap (chunks with |cx| <= gapHalf are not force-loaded).
            int gapHalf = stripGapHalf();
            Set<long[]> out = new LinkedHashSet<>();
            for (int cx = -stripHalf; cx <= stripHalf; cx++) {
                if (gapHalf > 0 && Math.abs(cx) <= gapHalf) {
                    continue;
                }
                for (int cz = -zRadius; cz <= zRadius; cz++) {
                    out.add(BenchRegionLoadLoops.pack(cx, cz));
                }
            }
            return out;
        }
        if (lobes < 2) {
            Set<long[]> out = new LinkedHashSet<>();
            for (int[] c : SPAWN_COLLAPSE_CHUNKS) {
                out.add(BenchRegionLoadLoops.pack(c[0], c[1]));
            }
            return out;
        }
        // Gap must exceed Folia's adjacency (≈2×emptySectionCreateRadius sections).
        // Default offset 40 chunks → ~20-chunk empty corridor at x≈0 — safe at grid-exponent 0–2.
        int offset = Math.max(16, Integer.getInteger("yap.bench.lobe_offset_chunks", 40));
        Set<long[]> out = new LinkedHashSet<>();
        for (int sign : new int[]{-1, 1}) {
            int ox = sign * offset;
            for (int[] c : LOBE_OFFSETS) {
                out.add(BenchRegionLoadLoops.pack(c[0] + ox, c[1]));
            }
        }
        return out;
    }

}

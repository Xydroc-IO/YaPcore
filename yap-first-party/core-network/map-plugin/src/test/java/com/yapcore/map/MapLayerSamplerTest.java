package com.yapcore.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MapLayerSamplerTest {

    @Test
    void surfaceKeepsHighestSolidOnly() {
        int[] col = {-1, 0x111111, 0x222222, -1, 0x333333};
        int[] ys = MapLayerSampler.selectWorldYs(col, 60, MapLayerSampler.LAYER_SURFACE, 48, 100);
        assertArrayEquals(new int[] {64}, ys); // minY 60 + index 4
    }

    @Test
    void caveKeepsOpenSolidsBelowSurface() {
        // yIndex: 0=bedrock, 1=cave floor (air above), 2=air, 3=dirt, 4=surface grass
        int[] col = {0x111111, 0xaaaaaa, -1, 0x8b4513, 0x5f9f35};
        int[] ys = MapLayerSampler.selectWorldYs(col, 0, MapLayerSampler.LAYER_CAVE, 48, 100);
        assertArrayEquals(new int[] {1}, ys);
    }

    @Test
    void fullKeepsAllSolids() {
        int[] col = {0x1, -1, 0x2};
        int[] ys = MapLayerSampler.selectWorldYs(col, 10, MapLayerSampler.LAYER_FULL, 48, 100);
        assertArrayEquals(new int[] {10, 12}, ys);
    }

    @Test
    void normalizeDefaultsUnknownToFull() {
        assertEquals(MapLayerSampler.LAYER_FULL, MapLayerSampler.normalizeMeshLayer("nope"));
        assertEquals(MapLayerSampler.LAYER_CAVE, MapLayerSampler.normalizeMeshLayer("CAVE"));
    }
}

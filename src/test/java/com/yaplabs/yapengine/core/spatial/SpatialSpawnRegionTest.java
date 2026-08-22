package com.yaplabs.yapengine.core.spatial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpatialSpawnRegionTest {

    @AfterEach
    void tearDown() {
        SpatialSpawnRegion.configure(false, 8);
    }

    @Test
    void disabledUsesCardinalsOnly() {
        SpatialSpawnRegion.configure(false, 8);
        assertEquals(SpatialQuadrant.SE, BitwiseQuadrantIndex.fromChunk(5, 5));
        assertEquals(SpatialQuadrant.NW, BitwiseQuadrantIndex.fromChunk(-5, -5));
        assertEquals(SpatialQuadrant.NE, BitwiseQuadrantIndex.fromChunk(5, -5));
        assertEquals(SpatialQuadrant.SW, BitwiseQuadrantIndex.fromChunk(-5, 5));
        assertFalse(SpatialSpawnRegion.containsChunk(0, 0));
    }

    @Test
    void enabledRoutesOriginBoxToSpawn() {
        SpatialSpawnRegion.configure(true, 2);
        assertEquals(SpatialQuadrant.SPAWN, BitwiseQuadrantIndex.fromChunk(0, 0));
        assertEquals(SpatialQuadrant.SPAWN, BitwiseQuadrantIndex.fromChunk(-2, 2));
        assertEquals(SpatialQuadrant.SPAWN, BitwiseQuadrantIndex.fromChunk(2, -1));
        // Just outside box → cardinal
        assertEquals(SpatialQuadrant.SE, BitwiseQuadrantIndex.fromChunk(3, 3));
        assertEquals(SpatialQuadrant.NW, BitwiseQuadrantIndex.fromChunk(-3, -3));
    }

    @Test
    void byIdPreservesSpawnWithoutMask() {
        assertEquals(SpatialQuadrant.SPAWN, SpatialQuadrant.byId(4));
        assertEquals(SpatialQuadrant.NW, SpatialQuadrant.byId(0));
        assertEquals(SpatialQuadrant.SE, SpatialQuadrant.byId(3));
    }
}

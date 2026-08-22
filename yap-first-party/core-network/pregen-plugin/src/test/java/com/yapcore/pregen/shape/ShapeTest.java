package com.yapcore.pregen.shape;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeTest {

    @Test
    void spiralRadius0IsOneChunk() {
        SpiralShape s = new SpiralShape(0, 0, 0);
        assertEquals(1, s.size());
    }

    @Test
    void spiralRadius1IsNineChunks() {
        SpiralShape s = new SpiralShape(0, 0, 1);
        assertEquals(9, s.size());
    }

    @Test
    void rectInclusive() {
        // blocks 0,0 to 15,15 = one chunk; 0,0 to 16,16 = 4 chunks
        RectShape one = new RectShape(0, 0, 15, 15);
        assertEquals(1, one.size());
        RectShape four = new RectShape(0, 0, 16, 16);
        assertEquals(4, four.size());
    }

    @Test
    void circleContainsCenter() {
        CircleShape c = new CircleShape(0, 0, 32);
        assertTrue(c.size() >= 1);
        boolean hasOrigin = false;
        for (ChunkPos p : c) {
            if (p.x() == 0 && p.z() == 0) {
                hasOrigin = true;
            }
        }
        assertTrue(hasOrigin);
    }

    @Test
    void polygonTriangle() {
        PolygonShape p = new PolygonShape(0, 0, 160, 0, 80, 160);
        assertTrue(p.size() > 0);
    }
}

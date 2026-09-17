package com.yapcore.portals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalCuboidTest {

    @Test
    void normalizesCornersAndContainsInclusive() {
        PortalCuboid c = PortalCuboid.of(10, 64, 10, 5, 70, 15);
        assertEquals(5, c.minX());
        assertEquals(10, c.maxX());
        assertEquals(64, c.minY());
        assertEquals(70, c.maxY());
        assertTrue(c.containsBlock(5, 64, 10));
        assertTrue(c.containsBlock(10, 70, 15));
        assertTrue(c.containsBlock(7, 65, 12));
        assertFalse(c.containsBlock(4, 64, 10));
        assertFalse(c.containsBlock(5, 63, 10));
        assertFalse(c.containsBlock(5, 64, 16));
    }

    @Test
    void volumeCountsInclusiveBlocks() {
        assertEquals(8, PortalCuboid.of(0, 0, 0, 1, 1, 1).volumeBlocks());
        assertEquals(1, PortalCuboid.of(3, 3, 3, 3, 3, 3).volumeBlocks());
    }
}

package com.yapcore.claims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimExpandRulesTest {

    @Test
    void plotAlignment() {
        ClaimExpandRules.Plot p = ClaimExpandRules.plotAt(65, -1, 64);
        assertEquals(64, p.minX());
        assertEquals(127, p.maxX());
        assertEquals(-64, p.minZ());
        assertEquals(-1, p.maxZ());
    }

    @Test
    void adjacentEast() {
        ClaimExpandRules.Plot from = ClaimExpandRules.plotAt(0, 0, 64);
        ClaimExpandRules.Plot east = ClaimExpandRules.adjacent(from, ClaimExpandRules.Dir.EAST, 64);
        assertEquals(64, east.minX());
        assertEquals(127, east.maxX());
        assertEquals(0, east.minZ());
        assertEquals(63, east.maxZ());
        assertTrue(ClaimExpandRules.sharesEdgeOrOverlaps(
                from.minX(), from.maxX(), from.minZ(), from.maxZ(),
                east.minX(), east.maxX(), east.minZ(), east.maxZ()));
    }

    @Test
    void diagonalDoesNotShareEdge() {
        assertFalse(ClaimExpandRules.sharesEdgeOrOverlaps(
                0, 63, 0, 63,
                64, 127, 64, 127));
    }

    @Test
    void yawToDir() {
        assertEquals(ClaimExpandRules.Dir.SOUTH, ClaimExpandRules.fromYaw(0f));
        assertEquals(ClaimExpandRules.Dir.WEST, ClaimExpandRules.fromYaw(90f));
        assertEquals(ClaimExpandRules.Dir.NORTH, ClaimExpandRules.fromYaw(180f));
        assertEquals(ClaimExpandRules.Dir.EAST, ClaimExpandRules.fromYaw(270f));
    }

    @Test
    void parseAliases() {
        assertEquals(ClaimExpandRules.Dir.NORTH, ClaimExpandRules.parseDir("n").orElseThrow());
        assertEquals(ClaimExpandRules.Dir.EAST, ClaimExpandRules.parseDir("east").orElseThrow());
        assertTrue(ClaimExpandRules.parseDir("nope").isEmpty());
    }
}

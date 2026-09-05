package com.yapcore.guard;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardHeuristicsTest {

    @Test
    void groundLikeMaterials() {
        assertFalse(GuardHeuristics.isGroundLike(Material.AIR));
        assertTrue(GuardHeuristics.isGroundLike(Material.STONE));
        assertTrue(GuardHeuristics.isGroundLike(Material.OAK_SLAB));
        assertTrue(GuardHeuristics.isGroundLike(Material.SCAFFOLDING));
    }

    @Test
    void speedAllowanceScalesWithSprintAndSensitivity() {
        double base = GuardHeuristics.speedAllowedBlocksPerTick(0.85, 0.5, false, false);
        double sprint = GuardHeuristics.speedAllowedBlocksPerTick(0.85, 0.5, true, false);
        assertTrue(sprint > base);
        assertEquals(0.85 * (0.5 + 0.5), base, 1e-9);
    }

    @Test
    void shouldFlagWithoutRandomSkip() {
        assertTrue(GuardHeuristics.shouldFlagSample(true, 0.1, false));
        assertFalse(GuardHeuristics.shouldFlagSample(false, 1.0, false));
    }
}

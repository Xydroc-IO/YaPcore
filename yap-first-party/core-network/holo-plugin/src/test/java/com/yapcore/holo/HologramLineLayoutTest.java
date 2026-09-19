package com.yapcore.holo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HologramLineLayoutTest {

    @Test
    void centersThreeLines() {
        assertEquals(0.28, HologramLineLayout.yOffset(0, 3, 0.28), 1e-9);
        assertEquals(0.0, HologramLineLayout.yOffset(1, 3, 0.28), 1e-9);
        assertEquals(-0.28, HologramLineLayout.yOffset(2, 3, 0.28), 1e-9);
    }

    @Test
    void singleLineAtOrigin() {
        assertEquals(0.0, HologramLineLayout.yOffset(0, 1, 0.28), 1e-9);
    }
}

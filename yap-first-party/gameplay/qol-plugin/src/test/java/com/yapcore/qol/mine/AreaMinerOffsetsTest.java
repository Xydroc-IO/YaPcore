package com.yapcore.qol.mine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure offset math for flat excavator planes. */
final class AreaMinerOffsetsTest {

    @Test
    void size3ProducesEightNeighbors() {
        assertEquals(8, countPlane(3));
    }

    @Test
    void size6ProducesThirtyFiveNeighbors() {
        assertEquals(35, countPlane(6));
    }

    @Test
    void size9ProducesEightyNeighbors() {
        assertEquals(80, countPlane(9));
    }

    /** Mirrors AreaMiner halfLow/halfHigh window without Bukkit. */
    private static int countPlane(int size) {
        int halfLow = size / 2;
        int halfHigh = size - halfLow - 1;
        int n = 0;
        for (int a = -halfLow; a <= halfHigh; a++) {
            for (int b = -halfLow; b <= halfHigh; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                n++;
            }
        }
        return n;
    }
}

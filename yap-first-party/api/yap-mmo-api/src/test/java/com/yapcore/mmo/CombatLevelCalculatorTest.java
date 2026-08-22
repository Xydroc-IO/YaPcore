package com.yapcore.mmo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatLevelCalculatorTest {

    @Test
    void usesOsrsWeightedFormula() {
        int level = CombatLevelCalculator.calculate(60, 60, 45, 50, 43, 70, 55);
        assertEquals(68, level);
    }

    @Test
    void pureMeleeBuild() {
        int level = CombatLevelCalculator.calculate(99, 99, 99, 99, 99, 1, 1);
        assertEquals(126, level);
    }
}

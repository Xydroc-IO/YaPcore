package com.yapcore.factions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactionPowerCalculatorTest {

    private static final FactionPowerCalculator.Config CFG =
            new FactionPowerCalculator.Config(50, 10, 100);

    @Test
    void maxPowerScalesWithMembers() {
        assertEquals(50, FactionPowerCalculator.maxPower(CFG, 0));
        assertEquals(80, FactionPowerCalculator.maxPower(CFG, 3));
    }

    @Test
    void claimCostFromArea() {
        assertEquals(1, FactionPowerCalculator.claimCost(CFG, 1));
        assertEquals(1, FactionPowerCalculator.claimCost(CFG, 100));
        assertEquals(2, FactionPowerCalculator.claimCost(CFG, 101));
        assertEquals(5, FactionPowerCalculator.claimCost(CFG, 500));
    }

    @Test
    void relationKeyOrdersIds() {
        var pair = FactionRelationKey.of(9, 3);
        assertEquals(3, pair.lowId());
        assertEquals(9, pair.highId());
        assertThrows(IllegalArgumentException.class, () -> FactionRelationKey.of(4, 4));
    }
}

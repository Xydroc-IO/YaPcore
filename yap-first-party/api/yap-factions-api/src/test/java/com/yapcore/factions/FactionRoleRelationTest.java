package com.yapcore.factions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionRoleRelationTest {

    @Test
    void roleAtLeastHierarchy() {
        assertTrue(FactionRole.LEADER.atLeast(FactionRole.OFFICER));
        assertTrue(FactionRole.OFFICER.atLeast(FactionRole.MEMBER));
        assertFalse(FactionRole.RECRUIT.atLeast(FactionRole.OFFICER));
        assertTrue(FactionRole.parse("officer").isPresent());
        assertTrue(FactionRole.parse("nope").isEmpty());
    }

    @Test
    void relationParseAndClamp() {
        assertEquals(FactionRelation.ALLY, FactionRelation.parse("ally").orElseThrow());
        assertTrue(FactionRelation.parse("").isEmpty());
        assertEquals(0, FactionPowerCalculator.clampPower(-5, 50));
        assertEquals(50, FactionPowerCalculator.clampPower(99, 50));
        assertEquals(12, FactionPowerCalculator.clampPower(12, 50));
    }
}

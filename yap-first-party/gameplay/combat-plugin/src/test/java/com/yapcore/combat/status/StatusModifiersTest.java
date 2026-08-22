package com.yapcore.combat.status;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusModifiersTest {

    @Test
    void mergeCombinesStatBoostsAndMultipliers() {
        StatusModifiers weak = new StatusModifiers(-2, -3, 0, 1.0, 1.0, 1.0, false);
        StatusModifiers vuln = new StatusModifiers(0, 0, 0, 1.0, 1.2, 1.0, false);
        StatusModifiers merged = weak.merge(vuln);
        assertEquals(-2, merged.attackBoost());
        assertEquals(-3, merged.strengthBoost());
        assertEquals(1.2, merged.incomingDamageMultiplier(), 0.001);
    }

    @Test
    void crowdControlBlocksAttacksAndSlowsMovement() {
        StatusModifiers stun = new StatusModifiers(0, 0, 0, 1.0, 1.0, 0.2, true);
        assertTrue(stun.blocksAttacks());
        assertEquals(0.2, stun.movementScale(), 0.001);
    }
}

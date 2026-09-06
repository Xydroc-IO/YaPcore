package com.yapcore.conquest;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConquestOverclaimRulesTest {

    @Test
    void vulnerableWhenPowerBelowLand() {
        assertTrue(ConquestOverclaimRules.isVulnerable(5, 10));
        assertFalse(ConquestOverclaimRules.isVulnerable(10, 10));
        assertFalse(ConquestOverclaimRules.isVulnerable(10, 0));
    }

    @Test
    void overclaimRequiresFeatureEnemyAndAfford() {
        assertTrue(ConquestOverclaimRules.canOverclaim(
                true, true, true, false, false, 2, 5, 0, 1, 50));
        assertFalse(ConquestOverclaimRules.canOverclaim(
                false, true, true, false, false, 2, 5, 0, 1, 50));
        assertFalse(ConquestOverclaimRules.canOverclaim(
                true, true, false, false, false, 2, 5, 0, 1, 50));
        assertFalse(ConquestOverclaimRules.canOverclaim(
                true, true, true, true, false, 2, 5, 0, 1, 50));
        assertFalse(ConquestOverclaimRules.canOverclaim(
                true, true, true, false, true, 2, 5, 0, 1, 50));
        assertFalse(ConquestOverclaimRules.canOverclaim(
                true, true, true, false, false, 2, 5, 50, 1, 50));
    }
}

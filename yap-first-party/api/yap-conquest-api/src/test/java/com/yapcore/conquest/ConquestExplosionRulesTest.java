package com.yapcore.conquest;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConquestExplosionRulesTest {

    @Test
    void safezoneDenyWins() {
        assertEquals(Optional.of(false), ConquestExplosionRules.evaluate(
                true, true, ConquestExplosionRules.ClaimedPolicy.ALLOW, Optional.of(false)));
    }

    @Test
    void claimedDenyWhenFeatureOn() {
        assertEquals(Optional.of(false), ConquestExplosionRules.evaluate(
                true, true, ConquestExplosionRules.ClaimedPolicy.DENY, Optional.empty()));
    }

    @Test
    void claimedAllowWhenFeatureOn() {
        assertEquals(Optional.of(true), ConquestExplosionRules.evaluate(
                true, true, ConquestExplosionRules.ClaimedPolicy.ALLOW, Optional.empty()));
    }

    @Test
    void featureOffPassesZoneOnly() {
        assertEquals(Optional.of(true), ConquestExplosionRules.evaluate(
                false, true, ConquestExplosionRules.ClaimedPolicy.DENY, Optional.of(true)));
        assertEquals(Optional.empty(), ConquestExplosionRules.evaluate(
                false, true, ConquestExplosionRules.ClaimedPolicy.DENY, Optional.empty()));
    }
}

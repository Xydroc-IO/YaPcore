package com.yapcore.claims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimExplosionRulesTest {

    @Test
    void denyFlagsBlockGrief() {
        assertFalse(ClaimExplosionRules.allowAtClaim(
                ClaimExplosionRules.Kind.CREEPER, true, false));
        assertFalse(ClaimExplosionRules.allowAtClaim(
                ClaimExplosionRules.Kind.TNT, false, true));
    }

    @Test
    void allowFlagsPermit() {
        assertTrue(ClaimExplosionRules.allowAtClaim(
                ClaimExplosionRules.Kind.CREEPER, false, true));
        assertTrue(ClaimExplosionRules.allowAtClaim(
                ClaimExplosionRules.Kind.TNT, true, false));
    }

    @Test
    void noneIsPassthrough() {
        assertEquals(ClaimExplosionRules.Kind.NONE, ClaimExplosionRules.kindOf(null));
        assertTrue(ClaimExplosionRules.allowAtClaim(
                ClaimExplosionRules.Kind.NONE, false, false));
    }
}

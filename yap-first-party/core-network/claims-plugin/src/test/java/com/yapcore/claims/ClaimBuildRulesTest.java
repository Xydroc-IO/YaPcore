package com.yapcore.claims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimBuildRulesTest {

    @Test
    void strangerCannotBuildInClaim() {
        assertFalse(ClaimBuildRules.allow(
                true, false, true, false, false, false, false));
    }

    @Test
    void ownerOrTrustedCanBuild() {
        assertTrue(ClaimBuildRules.allow(
                true, false, true, false, false, false, true));
    }

    @Test
    void taxFrozenBlocksEvenTrusted() {
        assertFalse(ClaimBuildRules.allow(
                true, false, true, false, false, true, true));
    }

    @Test
    void staffBypassAlways() {
        assertTrue(ClaimBuildRules.allow(
                true, true, true, false, false, true, false));
    }

    @Test
    void wildernessAllowedUnlessRequireClaim() {
        assertTrue(ClaimBuildRules.allow(
                true, false, false, false, false, false, false));
        assertFalse(ClaimBuildRules.allow(
                true, false, false, true, false, false, false));
        assertTrue(ClaimBuildRules.allow(
                true, false, false, true, true, false, false));
    }
}

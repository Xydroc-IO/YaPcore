package com.yapcore.conquest;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConquestZoneRulesTest {

    private static final ConquestZoneRules.Settings DEFAULT = new ConquestZoneRules.Settings(
            false, false, true, true,
            false, false, false, false,
            true, false, true, false, true, false, true);

    @Test
    void safezoneBlocksClaimPvpBuildExplode() {
        var p = ConquestZoneRules.policy(ConquestZoneType.SAFEZONE, DEFAULT);
        assertFalse(p.claimable());
        assertEquals(Optional.of(false), p.build());
        assertEquals(Optional.of(false), p.pvp());
        assertEquals(Optional.of(false), p.explode());
    }

    @Test
    void warzoneForcesPvpAndBlocksClaimByDefault() {
        assertFalse(ConquestZoneRules.canClaim(ConquestZoneType.WARZONE, DEFAULT));
        assertEquals(Optional.of(true), ConquestZoneRules.evaluatePvp(
                ConquestZoneType.WARZONE, DEFAULT, true, Optional.of(false)));
        assertEquals(Optional.of(false), ConquestZoneRules.evaluateBuildUnclaimed(
                ConquestZoneType.WARZONE, DEFAULT));
    }

    @Test
    void wildernessClaimableNoOverride() {
        assertTrue(ConquestZoneRules.canClaim(ConquestZoneType.WILDERNESS, DEFAULT));
        assertEquals(Optional.empty(), ConquestZoneRules.evaluateBuildUnclaimed(
                ConquestZoneType.WILDERNESS, DEFAULT));
        assertEquals(Optional.empty(), ConquestZoneRules.evaluatePvp(
                ConquestZoneType.WILDERNESS, DEFAULT, false, Optional.empty()));
    }

    @Test
    void safezoneOverridesClaimedFactionBuild() {
        assertEquals(Optional.of(false), ConquestZoneRules.evaluateBuild(
                ConquestZoneType.SAFEZONE, DEFAULT, true, Optional.of(true)));
    }

    @Test
    void claimedWildernessUsesFactionBuild() {
        assertEquals(Optional.of(true), ConquestZoneRules.evaluateBuild(
                ConquestZoneType.WILDERNESS, DEFAULT, true, Optional.of(true)));
    }

    @Test
    void warzoneClaimableWhenConfigured() {
        var s = new ConquestZoneRules.Settings(
                true, false, true, true,
                false, false, false, false,
                true, false, true, false, true, false, true);
        assertTrue(ConquestZoneRules.canClaim(ConquestZoneType.WARZONE, s));
    }
}

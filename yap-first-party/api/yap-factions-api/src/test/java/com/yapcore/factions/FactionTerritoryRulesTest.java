package com.yapcore.factions;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionTerritoryRulesTest {

    private static final long TERRITORY = 1L;
    private static final long OTHER = 2L;

    @Test
    void noOverlayNoOverride() {
        var ctx = base(false, false, false, null, null, FactionRelation.NEUTRAL, FactionRelation.NEUTRAL);
        assertEquals(Optional.empty(), FactionTerritoryRules.evaluateBuild(ctx));
        assertEquals(Optional.empty(), FactionTerritoryRules.evaluatePvp(ctx));
        assertEquals(Optional.empty(), FactionTerritoryRules.evaluateContainer(ctx));
    }

    @Test
    void claimOwnerBypassesBuildAndContainer() {
        var ctx = base(true, true, false, TERRITORY, null, FactionRelation.NEUTRAL, FactionRelation.NEUTRAL);
        assertEquals(Optional.empty(), FactionTerritoryRules.evaluateBuild(ctx));
        assertEquals(Optional.empty(), FactionTerritoryRules.evaluateContainer(ctx));
    }

    @Test
    void memberCanBuildAndOpenChests() {
        var ctx = base(true, false, false, TERRITORY, null, FactionRelation.NEUTRAL, FactionRelation.NEUTRAL);
        assertEquals(Optional.of(true), FactionTerritoryRules.evaluateBuild(ctx));
        assertEquals(Optional.of(true), FactionTerritoryRules.evaluateContainer(ctx));
    }

    @Test
    void allyCanBuildWhenConfigured() {
        var ctx = base(true, false, false, OTHER, null, FactionRelation.ALLY, FactionRelation.NEUTRAL);
        assertEquals(Optional.of(true), FactionTerritoryRules.evaluateBuild(ctx));
        assertEquals(Optional.of(true), FactionTerritoryRules.evaluateContainer(ctx));
    }

    @Test
    void outsiderDeniedBuild() {
        var ctx = base(true, false, false, OTHER, null, FactionRelation.ENEMY, FactionRelation.NEUTRAL);
        assertEquals(Optional.of(false), FactionTerritoryRules.evaluateBuild(ctx));
        assertEquals(Optional.of(false), FactionTerritoryRules.evaluateContainer(ctx));
    }

    @Test
    void shieldBlocksOutsiders() {
        var ctx = base(true, false, true, OTHER, null, FactionRelation.NEUTRAL, FactionRelation.NEUTRAL);
        assertEquals(Optional.of(false), FactionTerritoryRules.evaluateBuild(ctx));
        assertEquals(Optional.of(false), FactionTerritoryRules.evaluateContainer(ctx));
    }

    @Test
    void sameFactionPvpBlocked() {
        var ctx = base(true, false, false, TERRITORY, TERRITORY, FactionRelation.NEUTRAL, FactionRelation.NEUTRAL);
        assertEquals(Optional.of(false), FactionTerritoryRules.evaluatePvp(ctx));
    }

    @Test
    void allyPvpBlocked() {
        var ctx = base(true, false, false, TERRITORY, OTHER, FactionRelation.NEUTRAL, FactionRelation.ALLY);
        assertEquals(Optional.of(false), FactionTerritoryRules.evaluatePvp(ctx));
    }

    @Test
    void enemyPvpAllowedOnTerritory() {
        var ctx = base(true, false, false, TERRITORY, OTHER, FactionRelation.NEUTRAL, FactionRelation.ENEMY);
        assertEquals(Optional.of(true), FactionTerritoryRules.evaluatePvp(ctx));
    }

    @Test
    void shieldBlocksPvp() {
        var ctx = base(true, false, true, TERRITORY, OTHER, FactionRelation.NEUTRAL, FactionRelation.ENEMY);
        assertEquals(Optional.of(false), FactionTerritoryRules.evaluatePvp(ctx));
    }

    @Test
    void claimPowerGating() {
        assertTrue(FactionTerritoryRules.canAffordClaim(10, 5, 20));
        assertFalse(FactionTerritoryRules.canAffordClaim(16, 5, 20));
        assertTrue(FactionTerritoryRules.canAffordClaim(0, 1, 1));
    }

    private static FactionTerritoryRules.Context base(
            boolean overlay,
            boolean ownerIsActor,
            boolean shielded,
            Long actorFaction,
            Long victimFaction,
            FactionRelation actorToTerritory,
            FactionRelation attackerToVictim) {
        return new FactionTerritoryRules.Context(
                overlay,
                ownerIsActor,
                shielded,
                true,
                actorFaction,
                victimFaction,
                TERRITORY,
                actorToTerritory,
                attackerToVictim,
                true,
                true,
                true,
                true);
    }
}

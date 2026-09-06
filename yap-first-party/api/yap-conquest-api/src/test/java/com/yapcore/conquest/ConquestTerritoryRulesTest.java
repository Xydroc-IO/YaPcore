package com.yapcore.conquest;

import com.yapcore.factions.FactionRelation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConquestTerritoryRulesTest {

    @Test
    void wildernessNoOverride() {
        var ctx = ctx(false, false, null, null, FactionRelation.NEUTRAL, FactionRelation.NEUTRAL);
        assertEquals(Optional.empty(), ConquestTerritoryRules.evaluateBuild(ctx));
        assertEquals(Optional.empty(), ConquestTerritoryRules.evaluatePvp(ctx));
    }

    @Test
    void memberCanBuild() {
        var ctx = ctx(true, false, 1L, null, FactionRelation.NEUTRAL, FactionRelation.NEUTRAL);
        assertEquals(Optional.of(true), ConquestTerritoryRules.evaluateBuild(ctx));
    }

    @Test
    void allyCanBuild() {
        var ctx = ctx(true, false, 2L, null, FactionRelation.ALLY, FactionRelation.NEUTRAL);
        assertEquals(Optional.of(true), ConquestTerritoryRules.evaluateBuild(ctx));
    }

    @Test
    void outsiderDenied() {
        var ctx = ctx(true, false, 2L, null, FactionRelation.ENEMY, FactionRelation.NEUTRAL);
        assertEquals(Optional.of(false), ConquestTerritoryRules.evaluateBuild(ctx));
    }

    @Test
    void enemyPvpOnTerritory() {
        var ctx = ctx(true, false, 1L, 2L, FactionRelation.NEUTRAL, FactionRelation.ENEMY);
        assertEquals(Optional.of(true), ConquestTerritoryRules.evaluatePvp(ctx));
    }

    @Test
    void sameFactionPvpBlocked() {
        var ctx = ctx(true, false, 1L, 1L, FactionRelation.NEUTRAL, FactionRelation.NEUTRAL);
        assertEquals(Optional.of(false), ConquestTerritoryRules.evaluatePvp(ctx));
    }

    @Test
    void frozenBlocksOutsidersAndPvp() {
        var build = ctx(true, true, 2L, null, FactionRelation.NEUTRAL, FactionRelation.NEUTRAL);
        assertEquals(Optional.of(false), ConquestTerritoryRules.evaluateBuild(build));
        var pvp = ctx(true, true, 1L, 2L, FactionRelation.NEUTRAL, FactionRelation.ENEMY);
        assertEquals(Optional.of(false), ConquestTerritoryRules.evaluatePvp(pvp));
    }

    @Test
    void powerGate() {
        assertTrue(ConquestTerritoryRules.canAfford(0, 1, 10));
        assertFalse(ConquestTerritoryRules.canAfford(10, 1, 10));
    }

    private static ConquestTerritoryRules.Context ctx(
            boolean claimed,
            boolean frozen,
            Long actor,
            Long victim,
            FactionRelation toTerritory,
            FactionRelation atkToVic) {
        return new ConquestTerritoryRules.Context(
                claimed, frozen, actor, victim, 1L, toTerritory, atkToVic, true, true);
    }
}

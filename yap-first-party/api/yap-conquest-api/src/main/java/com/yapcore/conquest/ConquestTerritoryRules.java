package com.yapcore.conquest;

import com.yapcore.factions.FactionRelation;

import java.util.Optional;

/** Pure chunk territory rules (unit-tested). Empty = no conquest land here. */
public final class ConquestTerritoryRules {

    public record Context(
            boolean claimed,
            boolean frozen,
            Long actorFactionId,
            Long victimFactionId,
            long territoryFactionId,
            FactionRelation actorToTerritory,
            FactionRelation attackerToVictim,
            boolean alliesCanBuild,
            boolean enemyPvpOnly) {
    }

    private ConquestTerritoryRules() {
    }

    public static Optional<Boolean> evaluateBuild(Context ctx) {
        if (!ctx.claimed()) {
            return Optional.empty();
        }
        if (ctx.frozen()) {
            if (ctx.actorFactionId() == null || ctx.actorFactionId() != ctx.territoryFactionId()) {
                return Optional.of(false);
            }
        }
        if (ctx.actorFactionId() != null && ctx.actorFactionId() == ctx.territoryFactionId()) {
            return Optional.of(true);
        }
        if (ctx.actorFactionId() != null && ctx.alliesCanBuild()
                && ctx.actorToTerritory() == FactionRelation.ALLY) {
            return Optional.of(true);
        }
        return Optional.of(false);
    }

    public static Optional<Boolean> evaluatePvp(Context ctx) {
        if (!ctx.claimed()) {
            return Optional.empty();
        }
        if (ctx.frozen()) {
            return Optional.of(false);
        }
        if (ctx.actorFactionId() == null || ctx.victimFactionId() == null) {
            return Optional.empty();
        }
        if (ctx.actorFactionId().equals(ctx.victimFactionId())) {
            return Optional.of(false);
        }
        if (ctx.attackerToVictim() == FactionRelation.ALLY) {
            return Optional.of(false);
        }
        if (ctx.attackerToVictim() == FactionRelation.ENEMY && ctx.enemyPvpOnly()) {
            return Optional.of(true);
        }
        if (ctx.actorFactionId() == ctx.territoryFactionId()
                || ctx.victimFactionId() == ctx.territoryFactionId()) {
            return Optional.of(ctx.attackerToVictim() == FactionRelation.ENEMY);
        }
        return Optional.empty();
    }

    public static boolean canAfford(int usedPower, int claimCost, int maxPower) {
        return usedPower + claimCost <= maxPower;
    }
}

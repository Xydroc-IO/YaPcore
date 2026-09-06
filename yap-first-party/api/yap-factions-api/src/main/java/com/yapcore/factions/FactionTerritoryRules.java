package com.yapcore.factions;

/**
 * Pure territory rule decisions for overlay land (unit-tested).
 * Empty Optional = no faction override (caller uses normal claim rules).
 */
public final class FactionTerritoryRules {

    public record Context(
            boolean overlayPresent,
            boolean claimOwnerIsActor,
            boolean territoryShielded,
            boolean shieldBlocksPvp,
            Long actorFactionId,
            Long victimFactionId,
            long territoryFactionId,
            FactionRelation actorToTerritory,
            FactionRelation attackerToVictim,
            boolean alliesCanBuild,
            boolean enemyPvpOnly,
            boolean membersCanOpenChests,
            boolean alliesCanOpenChests) {
    }

    private FactionTerritoryRules() {
    }

    public static java.util.Optional<Boolean> evaluateBuild(Context ctx) {
        if (!ctx.overlayPresent()) {
            return java.util.Optional.empty();
        }
        if (ctx.claimOwnerIsActor()) {
            return java.util.Optional.empty();
        }
        if (ctx.territoryShielded()) {
            if (ctx.actorFactionId() == null || ctx.actorFactionId() != ctx.territoryFactionId()) {
                return java.util.Optional.of(false);
            }
        }
        if (ctx.actorFactionId() != null && ctx.actorFactionId() == ctx.territoryFactionId()) {
            return java.util.Optional.of(true);
        }
        if (ctx.actorFactionId() != null && ctx.alliesCanBuild()
                && ctx.actorToTerritory() == FactionRelation.ALLY) {
            return java.util.Optional.of(true);
        }
        return java.util.Optional.of(false);
    }

    public static java.util.Optional<Boolean> evaluatePvp(Context ctx) {
        if (!ctx.overlayPresent()) {
            return java.util.Optional.empty();
        }
        if (ctx.territoryShielded() && ctx.shieldBlocksPvp()) {
            return java.util.Optional.of(false);
        }
        if (ctx.actorFactionId() == null || ctx.victimFactionId() == null) {
            return java.util.Optional.empty();
        }
        if (ctx.actorFactionId().equals(ctx.victimFactionId())) {
            return java.util.Optional.of(false);
        }
        if (ctx.attackerToVictim() == FactionRelation.ALLY) {
            return java.util.Optional.of(false);
        }
        if (ctx.attackerToVictim() == FactionRelation.ENEMY && ctx.enemyPvpOnly()) {
            return java.util.Optional.of(true);
        }
        if (ctx.actorFactionId() == ctx.territoryFactionId()
                || ctx.victimFactionId() == ctx.territoryFactionId()) {
            return java.util.Optional.of(ctx.attackerToVictim() == FactionRelation.ENEMY);
        }
        return java.util.Optional.empty();
    }

    public static java.util.Optional<Boolean> evaluateContainer(Context ctx) {
        if (!ctx.overlayPresent()) {
            return java.util.Optional.empty();
        }
        if (ctx.claimOwnerIsActor()) {
            return java.util.Optional.empty();
        }
        if (ctx.territoryShielded()) {
            if (ctx.actorFactionId() == null || ctx.actorFactionId() != ctx.territoryFactionId()) {
                return java.util.Optional.of(false);
            }
        }
        if (ctx.actorFactionId() != null && ctx.actorFactionId() == ctx.territoryFactionId()) {
            return java.util.Optional.of(ctx.membersCanOpenChests());
        }
        if (ctx.actorFactionId() != null && ctx.alliesCanOpenChests()
                && ctx.actorToTerritory() == FactionRelation.ALLY) {
            return java.util.Optional.of(true);
        }
        return java.util.Optional.of(false);
    }

    /** Whether linking a claim is allowed given used power + cost vs max. */
    public static boolean canAffordClaim(int usedPower, int claimCost, int maxPower) {
        return usedPower + claimCost <= maxPower;
    }
}

package com.yapcore.conquest;

/** Pure overclaim rules (unit-tested). */
public final class ConquestOverclaimRules {

    private ConquestOverclaimRules() {
    }

    /**
     * Classic deficit: land costs more power than the faction currently has.
     * Shielded / frozen land is never overclaimable (caller enforces).
     */
    public static boolean isVulnerable(int currentPower, int landPowerUsed) {
        return landPowerUsed > 0 && currentPower < landPowerUsed;
    }

    public static boolean canOverclaim(
            boolean featureEnabled,
            boolean requireEnemy,
            boolean isEnemy,
            boolean defenderShielded,
            boolean chunkFrozen,
            int defenderPower,
            int defenderLandUsed,
            int attackerUsed,
            int claimCost,
            int attackerMaxPower) {
        if (!featureEnabled) {
            return false;
        }
        if (defenderShielded || chunkFrozen) {
            return false;
        }
        if (requireEnemy && !isEnemy) {
            return false;
        }
        if (!isVulnerable(defenderPower, defenderLandUsed)) {
            return false;
        }
        return ConquestTerritoryRules.canAfford(attackerUsed, claimCost, attackerMaxPower);
    }
}

package com.yapcore.claims;

/**
 * Pure build/break gate for claimed land (unit-testable without Bukkit).
 * Strangers need owner trust at BUILD+; staff bypass is separate.
 */
public final class ClaimBuildRules {

    private ClaimBuildRules() {
    }

    /**
     * @param inClaim              location is inside a claim
     * @param requireClaimToBuild  wilderness build requires a claim (or wilderness perm)
     * @param wildernessPerm       player may build in wilderness when requireClaimToBuild
     * @param taxFrozen            claim (or parent) tax-frozen
     * @param hasBuildTrust        owner or trusted at BUILD/MANAGE
     */
    public static boolean allow(
            boolean claimsEnabled,
            boolean staffBypass,
            boolean inClaim,
            boolean requireClaimToBuild,
            boolean wildernessPerm,
            boolean taxFrozen,
            boolean hasBuildTrust) {
        if (!claimsEnabled || staffBypass) {
            return true;
        }
        if (!inClaim) {
            return !requireClaimToBuild || wildernessPerm;
        }
        if (taxFrozen) {
            return false;
        }
        return hasBuildTrust;
    }
}

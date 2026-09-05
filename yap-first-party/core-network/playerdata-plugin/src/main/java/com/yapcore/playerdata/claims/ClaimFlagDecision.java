package com.yapcore.playerdata.claims;

import com.yapcore.regions.FlagValue;

/**
 * Pure claim-flag decision helpers (unit-testable without Bukkit).
 * Trust/bypass apply when a flag resolves to DENY; explosions are flag-only.
 */
public final class ClaimFlagDecision {

    private ClaimFlagDecision() {
    }

    /** Item drop / pickup / entry-style flags: ALLOW always; DENY unless bypass or trust. */
    public static boolean allowPlayerAction(FlagValue resolved, boolean staffBypass, boolean hasTrust) {
        if (staffBypass) {
            return true;
        }
        if (resolved == FlagValue.ALLOW) {
            return true;
        }
        return hasTrust;
    }

    /** TNT / creeper: ALLOW only (no trust path). */
    public static boolean allowExplosion(FlagValue resolved) {
        return resolved == FlagValue.ALLOW;
    }
}

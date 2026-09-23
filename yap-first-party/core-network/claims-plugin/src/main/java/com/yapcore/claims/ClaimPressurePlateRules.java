package com.yapcore.claims;

/**
 * Pure gate for non-player pressure-plate activation (unit-testable without Bukkit).
 * Stops mobs powering plates that open iron doors / gates via redstone.
 */
public final class ClaimPressurePlateRules {

    private ClaimPressurePlateRules() {
    }

    /**
     * @param claimsEnabled               YaPClaims master switch
     * @param mobsActivatePressurePlates  config: true = vanilla (mobs may press plates)
     * @param playerEntity                interacting entity is a player
     * @param pressurePlate               block is a pressure plate
     * @return true if the interact should be allowed
     */
    public static boolean allow(
            boolean claimsEnabled,
            boolean mobsActivatePressurePlates,
            boolean playerEntity,
            boolean pressurePlate) {
        if (!claimsEnabled || mobsActivatePressurePlates || playerEntity || !pressurePlate) {
            return true;
        }
        return false;
    }

    /** Material name heuristic — matches Bukkit pressure-plate types without Tag. */
    public static boolean isPressurePlate(String materialName) {
        return materialName != null && materialName.endsWith("PRESSURE_PLATE");
    }
}

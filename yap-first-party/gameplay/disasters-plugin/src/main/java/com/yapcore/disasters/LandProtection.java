package com.yapcore.disasters;

import com.yapcore.claims.ClaimLookups;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.regions.RegionServices;
import org.bukkit.Location;

/**
 * Gates disaster block changes against YaPRegions + YaPClaims.
 * System disasters never grief claimed land or BUILD-deny regions when protection is on.
 */
public final class LandProtection {

    private LandProtection() {
    }

    /** Whether environment/system code may place/break blocks at this location. */
    public static boolean canSystemModify(Location loc, DisastersConfig config) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (config == null) {
            return true;
        }
        if (!config.grief()) {
            return false;
        }
        if (config.protectRegions() && regionDeniesBuild(loc)) {
            return false;
        }
        if (config.protectClaims() && !ClaimLookups.canSystemModify(loc)) {
            return false;
        }
        return true;
    }

    /** Fire / lightning fire — also respects FIRE_SPREAD deny on regions/claims. */
    public static boolean canSystemIgnite(Location loc, DisastersConfig config) {
        if (!canSystemModify(loc, config)) {
            return false;
        }
        if (config.protectRegions() && regionDenies(loc, RegionFlag.FIRE_SPREAD)) {
            return false;
        }
        if (config.protectClaims() && claimDeniesFireSpread(loc)) {
            return false;
        }
        return true;
    }

    private static boolean regionDeniesBuild(Location loc) {
        return regionDenies(loc, RegionFlag.BUILD);
    }

    private static boolean regionDenies(Location loc, RegionFlag flag) {
        return RegionServices.find()
                .map(rs -> rs.flagAt(loc, flag) == FlagValue.DENY)
                .orElse(false);
    }

    private static boolean claimDeniesFireSpread(Location loc) {
        // Default claim flags deny fire-spread; without flag API here, any claim blocks system fire.
        return ClaimLookups.find().flatMap(lookup -> lookup.at(loc)).isPresent();
    }
}

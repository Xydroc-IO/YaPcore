package com.yapcore.disasters;

import com.yapcore.playerdata.PlayerDataPlugin;
import com.yapcore.playerdata.claims.Claim;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.regions.RegionServices;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * Gates disaster block changes against YaPRegions + YaPPlayerData claims.
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
        if (config.protectClaims() && claimBlocksSystemGrief(loc)) {
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
        if (config.protectClaims() && claimDeniesFlag(loc, RegionFlag.FIRE_SPREAD)) {
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

    private static boolean claimBlocksSystemGrief(Location loc) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("YaPPlayerData");
        if (!(plugin instanceof PlayerDataPlugin playerData) || !plugin.isEnabled()) {
            return false;
        }
        var claimOpt = playerData.claims().getAt(loc);
        // Any player claim is protected from system disaster grief.
        return claimOpt.isPresent();
    }

    private static boolean claimDeniesFlag(Location loc, RegionFlag flag) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("YaPPlayerData");
        if (!(plugin instanceof PlayerDataPlugin playerData) || !plugin.isEnabled()) {
            return false;
        }
        var claimOpt = playerData.claims().getAt(loc);
        if (claimOpt.isEmpty()) {
            return false;
        }
        Claim claim = claimOpt.get();
        return playerData.claims().flags().resolveOrDefault(claim.id(), flag) == FlagValue.DENY;
    }
}

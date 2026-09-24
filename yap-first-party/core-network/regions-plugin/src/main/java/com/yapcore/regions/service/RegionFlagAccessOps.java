package com.yapcore.regions.service;

import com.yapcore.regions.AdminRegion;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.sched.StaffBypass;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Player / location flag checks against loaded admin regions. */
final class RegionFlagAccessOps {

    private final RegionServiceImpl host;

    RegionFlagAccessOps(RegionServiceImpl host) {
        this.host = host;
    }

    boolean canBuild(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return host.flagAt(location, RegionFlag.BUILD) == FlagValue.ALLOW;
    }

    boolean canEnter(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return host.flagAt(location, RegionFlag.ENTRY) == FlagValue.ALLOW;
    }

    boolean isPvpAllowed(Player attacker, Player victim) {
        if (StaffBypass.land(attacker)) {
            return true;
        }
        Optional<AdminRegion> region = host.at(victim.getLocation());
        if (region.isEmpty()) {
            return true;
        }
        return host.resolve(region.get(), RegionFlag.PVP) == FlagValue.ALLOW;
    }

    boolean isDamageAllowed(Location location) {
        Optional<AdminRegion> region = host.at(location);
        if (region.isEmpty()) {
            return true;
        }
        return host.resolve(region.get(), RegionFlag.DAMAGE) == FlagValue.ALLOW;
    }

    boolean isMobDamageAllowed(Player victim) {
        Optional<AdminRegion> region = host.at(victim.getLocation());
        if (region.isEmpty()) {
            return true;
        }
        return host.resolve(region.get(), RegionFlag.MOB_DAMAGE) == FlagValue.ALLOW;
    }

    boolean isFireSpreadAllowed(Location location) {
        Optional<AdminRegion> region = host.at(location);
        if (region.isEmpty()) {
            return true;
        }
        return host.resolve(region.get(), RegionFlag.FIRE_SPREAD) == FlagValue.ALLOW;
    }

    boolean isMobSpawningAllowed(Location location) {
        Optional<AdminRegion> region = host.at(location);
        if (region.isEmpty()) {
            return true;
        }
        return host.resolve(region.get(), RegionFlag.MOB_SPAWNING) == FlagValue.ALLOW;
    }

    boolean isMobEntryAllowed(Location location) {
        Optional<AdminRegion> region = host.at(location);
        if (region.isEmpty()) {
            return true;
        }
        return host.resolve(region.get(), RegionFlag.MOB_ENTRY) == FlagValue.ALLOW;
    }

    boolean forcesClearWeather(Location location) {
        Optional<AdminRegion> region = host.at(location);
        if (region.isEmpty()) {
            return false;
        }
        return host.resolve(region.get(), RegionFlag.WEATHER) == FlagValue.DENY;
    }

    boolean canOpenContainer(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return host.flagAt(location, RegionFlag.CHEST_ACCESS) == FlagValue.ALLOW;
    }

    boolean canInteract(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return host.flagAt(location, RegionFlag.INTERACT) == FlagValue.ALLOW;
    }

    boolean canUse(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return host.flagAt(location, RegionFlag.USE) == FlagValue.ALLOW;
    }

    boolean canDropItems(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return host.flagAt(location, RegionFlag.ITEM_DROP) == FlagValue.ALLOW;
    }

    boolean canPickupItems(Player player, Location location) {
        if (StaffBypass.land(player)) {
            return true;
        }
        return host.flagAt(location, RegionFlag.ITEM_PICKUP) == FlagValue.ALLOW;
    }

    boolean isTntAllowed(Location location) {
        Optional<AdminRegion> region = host.at(location);
        if (region.isEmpty()) {
            return true;
        }
        return host.resolve(region.get(), RegionFlag.TNT) == FlagValue.ALLOW;
    }

    boolean isCreeperExplosionAllowed(Location location) {
        Optional<AdminRegion> region = host.at(location);
        if (region.isEmpty()) {
            return true;
        }
        return host.resolve(region.get(), RegionFlag.CREEPER_EXPLOSION) == FlagValue.ALLOW;
    }

    boolean isHungerAllowed(Location location) {
        return host.flagAt(location, RegionFlag.HUNGER) == FlagValue.ALLOW;
    }

    boolean isFarmlandTrampleAllowed(Location location) {
        return host.flagAt(location, RegionFlag.FARMLAND_TRAMPLE) == FlagValue.ALLOW;
    }

    boolean isItemFrameAllowed(Location location) {
        return host.flagAt(location, RegionFlag.ITEM_FRAME) == FlagValue.ALLOW;
    }

    boolean isArmorStandAllowed(Location location) {
        return host.flagAt(location, RegionFlag.ARMOR_STAND) == FlagValue.ALLOW;
    }

    boolean isNpcDamageAllowed(Location location) {
        Optional<AdminRegion> region = host.at(location);
        if (region.isEmpty()) {
            return false;
        }
        FlagValue explicit = region.get().flags().get(RegionFlag.NPC_DAMAGE);
        if (explicit != null) {
            return explicit == FlagValue.ALLOW;
        }
        return host.resolve(region.get(), RegionFlag.DAMAGE) == FlagValue.ALLOW;
    }

    boolean isLeafDecayAllowed(Location location) {
        return host.flagAt(location, RegionFlag.LEAF_DECAY) == FlagValue.ALLOW;
    }

    boolean isPistonsAllowed(Location location) {
        return host.flagAt(location, RegionFlag.PISTONS) == FlagValue.ALLOW;
    }

    boolean isVehiclePlaceAllowed(Location location) {
        return host.flagAt(location, RegionFlag.VEHICLE_PLACE) == FlagValue.ALLOW;
    }

    boolean isVehicleDestroyAllowed(Location location) {
        return host.flagAt(location, RegionFlag.VEHICLE_DESTROY) == FlagValue.ALLOW;
    }
}

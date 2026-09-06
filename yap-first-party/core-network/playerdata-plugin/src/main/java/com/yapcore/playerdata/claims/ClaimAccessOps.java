package com.yapcore.playerdata.claims;

import com.yapcore.factions.FactionService;
import com.yapcore.factions.FactionServices;
import com.yapcore.playerdata.db.ClaimRepository;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.sched.StaffBypass;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Build / access / PVP / flag checks and trust resolution for claims. */
final class ClaimAccessOps {

    private final ClaimService host;

    ClaimAccessOps(ClaimService host) {
        this.host = host;
    }

    boolean canBuild(Player player, Location loc) {
        if (!host.config().claimsEnabled()) {
            return true;
        }
        if (StaffBypass.land(player)) {
            return true;
        }
        Optional<Claim> claim = host.getAt(loc);
        if (claim.isEmpty()) {
            return !host.config().claimsRequireClaimToBuild() || player.hasPermission("yapdata.claims.wilderness");
        }
        Claim c = claim.get();
        Optional<Boolean> factionBuild = factionBuildOverride(player, c);
        if (factionBuild.isPresent()) {
            return factionBuild.get();
        }
        if (!flagAllowsBuild(c, player)) {
            return false;
        }
        if (c.taxFrozen()) {
            return false;
        }
        Claim check = c;
        if (c.isSubdivision()) {
            // parent frozen freezes subs
            Optional<Claim> parent = host.getTopLevelAt(loc);
            if (parent.isPresent() && parent.get().taxFrozen()) {
                return false;
            }
        }
        return hasTrust(check, player.getUniqueId(), ClaimRepository.TrustLevel.BUILD);
    }

    boolean canAccess(Player player, Location loc) {
        if (!host.config().claimsEnabled()) {
            return true;
        }
        if (StaffBypass.land(player)) {
            return true;
        }
        Optional<Claim> claim = host.getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        if (claim.get().taxFrozen() && !claim.get().owner().equals(player.getUniqueId())) {
            return false;
        }
        if (!flagAllowsInteract(claim.get(), player)) {
            return false;
        }
        return hasTrust(claim.get(), player.getUniqueId(), ClaimRepository.TrustLevel.ACCESS);
    }

    boolean canEnter(Player player, Location loc) {
        if (!host.config().claimsEnabled() || StaffBypass.land(player)) {
            return true;
        }
        Optional<Claim> claim = host.getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        FlagValue entry = host.flags().resolveOrDefault(claim.get().id(), RegionFlag.ENTRY);
        if (entry == FlagValue.ALLOW) {
            return true;
        }
        return hasTrust(claim.get(), player.getUniqueId(), ClaimRepository.TrustLevel.ACCESS);
    }

    boolean isPvpAllowed(Player attacker, Player victim) {
        if (!host.config().claimsEnabled()) {
            return true;
        }
        var claim = host.getAt(victim.getLocation());
        if (claim.isEmpty()) {
            return true;
        }
        Optional<Boolean> factionPvp = factionPvpOverride(attacker, victim, claim.get().id());
        if (factionPvp.isPresent()) {
            return factionPvp.get();
        }
        FlagValue pvp = host.flags().resolveOrDefault(claim.get().id(), RegionFlag.PVP);
        if (pvp == FlagValue.DENY) {
            return StaffBypass.land(attacker)
                    || hasTrust(claim.get(), attacker.getUniqueId(), ClaimRepository.TrustLevel.BUILD);
        }
        return true;
    }

    private Optional<Boolean> factionBuildOverride(Player player, Claim claim) {
        Optional<FactionService> factions = FactionServices.find();
        if (factions.isEmpty()) {
            return Optional.empty();
        }
        return factions.get().evaluateBuild(player, claim.id(), claim.owner());
    }

    private Optional<Boolean> factionPvpOverride(Player attacker, Player victim, long claimId) {
        Optional<FactionService> factions = FactionServices.find();
        if (factions.isEmpty()) {
            return Optional.empty();
        }
        return factions.get().evaluatePvp(attacker, victim, claimId);
    }

    boolean isMobDamageAllowed(Player victim) {
        if (!host.config().claimsEnabled()) {
            return true;
        }
        var claim = host.getAt(victim.getLocation());
        if (claim.isEmpty()) {
            return true;
        }
        return host.flags().resolveOrDefault(claim.get().id(), RegionFlag.MOB_DAMAGE) == FlagValue.ALLOW;
    }

    boolean isFireSpreadAllowed(Location loc) {
        if (!host.config().claimsEnabled()) {
            return true;
        }
        var claim = host.getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        return host.flags().resolveOrDefault(claim.get().id(), RegionFlag.FIRE_SPREAD) == FlagValue.ALLOW;
    }

    boolean isMobSpawningAllowed(Location loc) {
        if (!host.config().claimsEnabled()) {
            return true;
        }
        var claim = host.getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        return host.flags().resolveOrDefault(claim.get().id(), RegionFlag.MOB_SPAWNING) == FlagValue.ALLOW;
    }

    boolean canDropItems(Player player, Location loc) {
        if (!host.config().claimsEnabled()) {
            return true;
        }
        Optional<Claim> claim = host.getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        FlagValue drop = host.flags().resolveOrDefault(claim.get().id(), RegionFlag.ITEM_DROP);
        return ClaimFlagDecision.allowPlayerAction(
                drop, StaffBypass.land(player),
                hasTrust(claim.get(), player.getUniqueId(), ClaimRepository.TrustLevel.ACCESS));
    }

    boolean canPickupItems(Player player, Location loc) {
        if (!host.config().claimsEnabled()) {
            return true;
        }
        Optional<Claim> claim = host.getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        FlagValue pickup = host.flags().resolveOrDefault(claim.get().id(), RegionFlag.ITEM_PICKUP);
        return ClaimFlagDecision.allowPlayerAction(
                pickup, StaffBypass.land(player),
                hasTrust(claim.get(), player.getUniqueId(), ClaimRepository.TrustLevel.ACCESS));
    }

    boolean isTntAllowed(Location loc) {
        if (!host.config().claimsEnabled()) {
            return true;
        }
        var claim = host.getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        return ClaimFlagDecision.allowExplosion(
                host.flags().resolveOrDefault(claim.get().id(), RegionFlag.TNT));
    }

    boolean isCreeperExplosionAllowed(Location loc) {
        if (!host.config().claimsEnabled()) {
            return true;
        }
        var claim = host.getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        return ClaimFlagDecision.allowExplosion(
                host.flags().resolveOrDefault(claim.get().id(), RegionFlag.CREEPER_EXPLOSION));
    }

    private boolean flagAllowsBuild(Claim claim, Player player) {
        var explicit = host.flags().explicit(claim.id(), RegionFlag.BUILD);
        if (explicit.isPresent() && explicit.get() == FlagValue.DENY) {
            return false;
        }
        if (explicit.isPresent() && explicit.get() == FlagValue.ALLOW) {
            return hasTrust(claim, player.getUniqueId(), ClaimRepository.TrustLevel.BUILD);
        }
        return true;
    }

    private boolean flagAllowsInteract(Claim claim, Player player) {
        var explicit = host.flags().explicit(claim.id(), RegionFlag.INTERACT);
        if (explicit.isPresent() && explicit.get() == FlagValue.DENY) {
            return false;
        }
        return true;
    }

    boolean canOpenContainer(Player player, Location loc) {
        if (!canAccess(player, loc)) {
            return false;
        }
        Optional<Claim> claim = host.getAt(loc);
        if (claim.isEmpty()) {
            return true;
        }
        Optional<Boolean> factionChest = factionContainerOverride(player, claim.get());
        if (factionChest.isPresent()) {
            return factionChest.get();
        }
        FlagValue chest = host.flags().resolveOrDefault(claim.get().id(), RegionFlag.CHEST_ACCESS);
        if (chest == FlagValue.DENY) {
            return false;
        }
        return hasTrust(claim.get(), player.getUniqueId(), ClaimRepository.TrustLevel.ACCESS);
    }

    private Optional<Boolean> factionContainerOverride(Player player, Claim claim) {
        Optional<FactionService> factions = FactionServices.find();
        if (factions.isEmpty()) {
            return Optional.empty();
        }
        return factions.get().evaluateContainer(player, claim.id(), claim.owner());
    }

    boolean hasTrust(Claim claim, UUID player, ClaimRepository.TrustLevel needed) {
        if (claim.owner().equals(player)) {
            return true;
        }
        // subclaim trust first; fall back to parent trust
        Map<UUID, ClaimRepository.TrustLevel> map = host.trustCacheMutable().computeIfAbsent(claim.id(), id -> {
            try {
                return new ConcurrentHashMap<>(host.repo().trustMap(id));
            } catch (SQLException e) {
                return new ConcurrentHashMap<>();
            }
        });
        ClaimRepository.TrustLevel level = map.get(player);
        if (level != null && level.atLeast(needed)) {
            return true;
        }
        if (claim.isSubdivision()) {
            try {
                Optional<Claim> parent = host.repo().get(claim.parentId());
                if (parent.isPresent() && parent.get().owner().equals(player)) {
                    return true;
                }
                if (parent.isPresent()) {
                    return hasTrustDirect(parent.get(), player, needed);
                }
            } catch (SQLException ignored) {
            }
        }
        return false;
    }

    private boolean hasTrustDirect(Claim claim, UUID player, ClaimRepository.TrustLevel needed) {
        if (claim.owner().equals(player)) {
            return true;
        }
        Map<UUID, ClaimRepository.TrustLevel> map = host.trustCacheMutable().computeIfAbsent(claim.id(), id -> {
            try {
                return new ConcurrentHashMap<>(host.repo().trustMap(id));
            } catch (SQLException e) {
                return new ConcurrentHashMap<>();
            }
        });
        ClaimRepository.TrustLevel level = map.get(player);
        return level != null && level.atLeast(needed);
    }
}

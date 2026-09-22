package com.yapcore.factions.integration;

import com.yapcore.claims.Claim;
import com.yapcore.claims.ClaimService;
import com.yapcore.claims.ClaimsPlugin;
import com.yapcore.claims.db.ClaimRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/** Soft bridge to YaPClaims (no schema changes). */
public final class ClaimIntegration {

    private ClaimIntegration() {
    }

    public static Optional<ClaimService> claims() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("YaPClaims");
        if (!(plugin instanceof ClaimsPlugin claimsPlugin) || !plugin.isEnabled()) {
            return Optional.empty();
        }
        ClaimService service = claimsPlugin.claims();
        return service == null ? Optional.empty() : Optional.of(service);
    }

    public static Optional<Claim> claimAt(Player player) {
        return claims().flatMap(s -> s.getAt(player.getLocation()));
    }

    public static Optional<Claim> claimAt(org.bukkit.Location location) {
        return claims().flatMap(s -> s.getAt(location));
    }

    public static List<Claim> manageableClaims(Player player) {
        return claims().map(s -> s.manageableBy(player)).orElse(List.of());
    }

    public static boolean canManageClaim(Player player, Claim claim) {
        if (claim.owner().equals(player.getUniqueId())) {
            return true;
        }
        return claims().map(s -> s.hasTrust(claim, player.getUniqueId(), ClaimRepository.TrustLevel.MANAGE))
                .orElse(false);
    }

    public static boolean isAdmin(Player player) {
        return player.hasPermission("yapdata.claims.admin");
    }

    public static void setTaxFrozen(long claimId, boolean frozen) {
        claims().ifPresent(s -> s.setTaxFrozen(claimId, frozen));
    }
}

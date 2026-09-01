package com.yapcore.factions.integration;

import com.yapcore.playerdata.PlayerDataPlugin;
import com.yapcore.playerdata.claims.Claim;
import com.yapcore.playerdata.claims.ClaimService;
import com.yapcore.playerdata.db.ClaimRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Soft bridge to YaPPlayerData claims (no schema changes). */
public final class ClaimIntegration {

    private ClaimIntegration() {
    }

    public static Optional<ClaimService> claims() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("YaPPlayerData");
        if (!(plugin instanceof PlayerDataPlugin playerData) || !plugin.isEnabled()) {
            return Optional.empty();
        }
        return Optional.of(playerData.claims());
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
}

package com.yapcore.claims;

import org.bukkit.Bukkit;

import java.util.Optional;

/** Resolves the registered {@link ClaimLookup} when YaPClaims is on. */
public final class ClaimLookups {

    private ClaimLookups() {
    }

    public static Optional<ClaimLookup> find() {
        var reg = Bukkit.getServicesManager().getRegistration(ClaimLookup.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }

    /** True when no claim covers the block, or claims are offline. */
    public static boolean isWilderness(org.bukkit.Location location) {
        return find().map(l -> l.isWilderness(location)).orElse(true);
    }

    /** False when a claim protects the block; true if claims offline or wilderness. */
    public static boolean canSystemModify(org.bukkit.Location location) {
        return find().map(l -> l.canSystemModify(location)).orElse(true);
    }
}

package com.yapcore.claims;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/** Lookup for {@link ClaimFeatures} published by YaPClaims. */
public final class ClaimFeaturesProvider {

    private ClaimFeaturesProvider() {
    }

    public static Optional<ClaimFeatures> find() {
        RegisteredServiceProvider<ClaimFeatures> rsp =
                Bukkit.getServicesManager().getRegistration(ClaimFeatures.class);
        return rsp == null ? Optional.empty() : Optional.of(rsp.getProvider());
    }
}

package com.yapcore.playerdata;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/** Lookup for {@link PlayerFeatures} published by YaPPlayerData. */
public final class PlayerFeaturesProvider {

    private PlayerFeaturesProvider() {
    }

    public static Optional<PlayerFeatures> find() {
        RegisteredServiceProvider<PlayerFeatures> rsp =
                Bukkit.getServicesManager().getRegistration(PlayerFeatures.class);
        return rsp == null ? Optional.empty() : Optional.of(rsp.getProvider());
    }
}

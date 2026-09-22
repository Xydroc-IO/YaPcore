package com.yapcore.playerdata;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/** Lookup for {@link HomeAccess}. */
public final class HomeAccessProvider {

    private HomeAccessProvider() {
    }

    public static Optional<HomeAccess> find() {
        RegisteredServiceProvider<HomeAccess> rsp =
                Bukkit.getServicesManager().getRegistration(HomeAccess.class);
        if (rsp == null) {
            return Optional.empty();
        }
        HomeAccess service = rsp.getProvider();
        return service == null ? Optional.empty() : Optional.of(service);
    }
}

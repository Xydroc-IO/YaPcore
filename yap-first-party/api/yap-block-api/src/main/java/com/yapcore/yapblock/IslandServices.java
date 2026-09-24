package com.yapcore.yapblock;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public final class IslandServices {

    private IslandServices() {
    }

    public static Optional<IslandService> find() {
        RegisteredServiceProvider<IslandService> rsp =
                Bukkit.getServicesManager().getRegistration(IslandService.class);
        if (rsp == null) {
            return Optional.empty();
        }
        IslandService service = rsp.getProvider();
        return service == null ? Optional.empty() : Optional.of(service);
    }
}

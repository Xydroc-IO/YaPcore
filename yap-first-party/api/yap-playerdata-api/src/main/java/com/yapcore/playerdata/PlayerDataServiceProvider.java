package com.yapcore.playerdata;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public final class PlayerDataServiceProvider {

    private PlayerDataServiceProvider() {
    }

    public static Optional<PlayerDataService> find() {
        RegisteredServiceProvider<PlayerDataService> rsp =
                Bukkit.getServicesManager().getRegistration(PlayerDataService.class);
        if (rsp == null) {
            return Optional.empty();
        }
        PlayerDataService service = rsp.getProvider();
        return service == null ? Optional.empty() : Optional.of(service);
    }
}

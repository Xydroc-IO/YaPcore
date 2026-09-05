package com.yapcore.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public final class DungeonServices {

    private DungeonServices() {
    }

    public static Optional<DungeonService> find() {
        RegisteredServiceProvider<DungeonService> rsp =
                Bukkit.getServicesManager().getRegistration(DungeonService.class);
        if (rsp == null) {
            return Optional.empty();
        }
        DungeonService service = rsp.getProvider();
        return service == null ? Optional.empty() : Optional.of(service);
    }
}

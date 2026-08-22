package com.yapcore.protect;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public final class ProtectServiceProvider {

    private ProtectServiceProvider() {
    }

    public static Optional<ProtectService> find() {
        RegisteredServiceProvider<ProtectService> rsp =
                Bukkit.getServicesManager().getRegistration(ProtectService.class);
        if (rsp == null) {
            return Optional.empty();
        }
        ProtectService service = rsp.getProvider();
        return service == null ? Optional.empty() : Optional.of(service);
    }
}

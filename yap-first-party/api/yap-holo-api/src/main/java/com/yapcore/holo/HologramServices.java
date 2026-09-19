package com.yapcore.holo;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Bukkit service lookup for YaPHolo. */
public final class HologramServices {

    private HologramServices() {
    }

    public static HologramService holograms() {
        RegisteredServiceProvider<HologramService> rsp =
                Bukkit.getServicesManager().getRegistration(HologramService.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public static HologramService require() {
        HologramService service = holograms();
        if (service == null) {
            throw new IllegalStateException("YaPHolo HologramService is not registered — install yap-holo.jar");
        }
        return service;
    }
}

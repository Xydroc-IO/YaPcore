package com.yapcore.lib;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Bukkit service lookup for YaPLib. */
public final class PacketServices {

    private PacketServices() {
    }

    public static PacketService packets() {
        RegisteredServiceProvider<PacketService> rsp =
                Bukkit.getServicesManager().getRegistration(PacketService.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public static PacketService requirePackets() {
        PacketService service = packets();
        if (service == null) {
            throw new IllegalStateException("YaPLib PacketService is not registered — install yap-lib.jar");
        }
        return service;
    }
}

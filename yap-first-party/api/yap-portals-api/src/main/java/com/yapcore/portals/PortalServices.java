package com.yapcore.portals;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class PortalServices {

    private PortalServices() {
    }

    public static Optional<PortalService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(PortalService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }

    public static Optional<PortalTransfer> transfer() {
        var reg = Bukkit.getServicesManager().getRegistration(PortalTransfer.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

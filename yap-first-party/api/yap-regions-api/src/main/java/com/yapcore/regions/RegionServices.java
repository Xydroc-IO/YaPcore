package com.yapcore.regions;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class RegionServices {

    private RegionServices() {
    }

    public static Optional<RegionService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(RegionService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

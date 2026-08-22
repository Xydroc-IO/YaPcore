package com.yapcore.factions;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class FactionServices {

    private FactionServices() {
    }

    public static Optional<FactionService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(FactionService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

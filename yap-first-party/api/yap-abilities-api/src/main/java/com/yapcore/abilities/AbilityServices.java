package com.yapcore.abilities;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class AbilityServices {

    private AbilityServices() {
    }

    public static Optional<AbilityService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(AbilityService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

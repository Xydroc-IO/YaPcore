package com.yapcore.mechanics;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class MechanicsServices {

    private MechanicsServices() {
    }

    public static Optional<MechanicsService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(MechanicsService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

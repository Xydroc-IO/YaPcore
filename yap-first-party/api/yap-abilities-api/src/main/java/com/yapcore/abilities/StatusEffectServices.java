package com.yapcore.abilities;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class StatusEffectServices {

    private StatusEffectServices() {
    }

    public static Optional<StatusEffectService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(StatusEffectService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

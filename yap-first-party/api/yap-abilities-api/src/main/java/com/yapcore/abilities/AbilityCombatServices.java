package com.yapcore.abilities;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class AbilityCombatServices {

    private AbilityCombatServices() {
    }

    public static Optional<AbilityCombatBridge> find() {
        var reg = Bukkit.getServicesManager().getRegistration(AbilityCombatBridge.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

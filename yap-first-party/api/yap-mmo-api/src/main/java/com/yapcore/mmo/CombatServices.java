package com.yapcore.mmo;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class CombatServices {

    private CombatServices() {
    }

    public static Optional<CombatService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(CombatService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

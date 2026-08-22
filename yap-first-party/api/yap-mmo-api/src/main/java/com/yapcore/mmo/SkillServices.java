package com.yapcore.mmo;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class SkillServices {

    private SkillServices() {
    }

    public static Optional<SkillService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(SkillService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

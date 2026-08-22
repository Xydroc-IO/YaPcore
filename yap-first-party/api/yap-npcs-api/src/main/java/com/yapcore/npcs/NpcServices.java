package com.yapcore.npcs;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class NpcServices {

    private NpcServices() {
    }

    public static Optional<NpcService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(NpcService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

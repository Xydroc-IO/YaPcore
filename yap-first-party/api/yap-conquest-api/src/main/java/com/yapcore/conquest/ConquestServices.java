package com.yapcore.conquest;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class ConquestServices {

    private ConquestServices() {
    }

    public static Optional<ConquestService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(ConquestService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

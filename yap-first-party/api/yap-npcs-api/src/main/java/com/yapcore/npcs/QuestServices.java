package com.yapcore.npcs;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class QuestServices {

    private QuestServices() {
    }

    public static Optional<QuestService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(QuestService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

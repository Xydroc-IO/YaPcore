package com.yapcore.guilds;

import org.bukkit.Bukkit;

import java.util.Optional;

public final class GuildServices {

    private GuildServices() {
    }

    public static Optional<GuildService> find() {
        var reg = Bukkit.getServicesManager().getRegistration(GuildService.class);
        return reg == null ? Optional.empty() : Optional.of(reg.getProvider());
    }
}

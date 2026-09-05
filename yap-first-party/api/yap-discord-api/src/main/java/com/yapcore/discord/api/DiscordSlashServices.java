package com.yapcore.discord.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/** Lookup helper for {@link DiscordSlashRegistrar}. */
public final class DiscordSlashServices {

    private DiscordSlashServices() {
    }

    public static Optional<DiscordSlashRegistrar> find() {
        RegisteredServiceProvider<DiscordSlashRegistrar> rsp =
                Bukkit.getServicesManager().getRegistration(DiscordSlashRegistrar.class);
        if (rsp == null) {
            return Optional.empty();
        }
        DiscordSlashRegistrar registrar = rsp.getProvider();
        return registrar == null ? Optional.empty() : Optional.of(registrar);
    }
}

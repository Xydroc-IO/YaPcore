package com.yapcore.bedrockblocks;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/** Lookup helper for {@link PortBlocksService}. */
public final class PortBlocksServiceProvider {

    private PortBlocksServiceProvider() {
    }

    public static Optional<PortBlocksService> get() {
        RegisteredServiceProvider<PortBlocksService> rsp =
                Bukkit.getServicesManager().getRegistration(PortBlocksService.class);
        return rsp == null ? Optional.empty() : Optional.ofNullable(rsp.getProvider());
    }
}

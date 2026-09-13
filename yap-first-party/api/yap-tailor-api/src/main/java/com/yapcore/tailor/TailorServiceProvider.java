package com.yapcore.tailor;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/** Lookup helper for the shared {@link TailorService} (YaPTailor plugin). */
public final class TailorServiceProvider {

    private TailorServiceProvider() {
    }

    public static Optional<TailorService> find() {
        try {
            if (Bukkit.getServer() == null) {
                return Optional.empty();
            }
            RegisteredServiceProvider<TailorService> rsp =
                    Bukkit.getServicesManager().getRegistration(TailorService.class);
            if (rsp == null) {
                return Optional.empty();
            }
            TailorService service = rsp.getProvider();
            return service == null ? Optional.empty() : Optional.of(service);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    public static boolean available() {
        return find().isPresent();
    }
}

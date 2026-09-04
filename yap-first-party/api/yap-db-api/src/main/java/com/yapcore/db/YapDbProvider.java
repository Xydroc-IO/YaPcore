package com.yapcore.db;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/**
 * Lookup helper for the shared {@link YapDb} service (YaPDB plugin).
 */
public final class YapDbProvider {

    private YapDbProvider() {
    }

    /** Registered service, if YaPDB is enabled and the pool is open. */
    public static Optional<YapDb> find() {
        try {
            if (Bukkit.getServer() == null) {
                return Optional.empty();
            }
            RegisteredServiceProvider<YapDb> rsp = Bukkit.getServicesManager().getRegistration(YapDb.class);
            if (rsp == null) {
                return Optional.empty();
            }
            YapDb db = rsp.getProvider();
            return db != null && db.isOpen() ? Optional.of(db) : Optional.empty();
        } catch (Throwable ignored) {
            // No Bukkit server (unit tests) or services manager unavailable
            return Optional.empty();
        }
    }

    public static boolean available() {
        return find().isPresent();
    }
}

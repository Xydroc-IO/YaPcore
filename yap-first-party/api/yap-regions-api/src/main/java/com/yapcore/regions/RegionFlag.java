package com.yapcore.regions;

import java.util.Locale;
import java.util.Optional;

/** WorldGuard-class region flags for claims and admin regions. */
public enum RegionFlag {
    PVP,
    MOB_DAMAGE,
    BUILD,
    INTERACT,
    ENTRY,
    CHEST_ACCESS,
    FIRE_SPREAD,
    MOB_SPAWNING,
    ITEM_DROP,
    ITEM_PICKUP,
    TNT,
    CREEPER_EXPLOSION;

    public static Optional<RegionFlag> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Optional.of(valueOf(norm));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

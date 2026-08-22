package com.yapcore.factions;

import java.util.Locale;
import java.util.Optional;

public enum FactionRelation {
    ALLY,
    ENEMY,
    NEUTRAL;

    public static Optional<FactionRelation> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

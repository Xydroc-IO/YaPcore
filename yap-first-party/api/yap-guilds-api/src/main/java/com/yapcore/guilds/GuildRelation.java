package com.yapcore.guilds;

import java.util.Locale;
import java.util.Optional;

public enum GuildRelation {
    ALLY,
    ENEMY,
    NEUTRAL;

    public static Optional<GuildRelation> parse(String raw) {
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

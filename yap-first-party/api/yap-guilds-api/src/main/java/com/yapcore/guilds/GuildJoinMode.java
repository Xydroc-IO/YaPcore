package com.yapcore.guilds;

import java.util.Locale;
import java.util.Optional;

public enum GuildJoinMode {
    OPEN,
    INVITE,
    CLOSED;

    public static Optional<GuildJoinMode> parse(String raw) {
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

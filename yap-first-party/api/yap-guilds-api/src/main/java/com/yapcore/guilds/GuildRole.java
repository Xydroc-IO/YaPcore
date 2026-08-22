package com.yapcore.guilds;

import java.util.Locale;
import java.util.Optional;

public enum GuildRole {
    LEADER,
    OFFICER,
    VETERAN,
    MEMBER,
    RECRUIT;

    public boolean atLeast(GuildRole needed) {
        return ordinal() <= needed.ordinal();
    }

    public static Optional<GuildRole> parse(String raw) {
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

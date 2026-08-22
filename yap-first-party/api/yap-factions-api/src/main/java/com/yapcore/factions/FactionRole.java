package com.yapcore.factions;

import java.util.Locale;
import java.util.Optional;

public enum FactionRole {
    LEADER,
    OFFICER,
    MEMBER,
    RECRUIT;

    public boolean atLeast(FactionRole needed) {
        return ordinal() <= needed.ordinal();
    }

    public static Optional<FactionRole> parse(String raw) {
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

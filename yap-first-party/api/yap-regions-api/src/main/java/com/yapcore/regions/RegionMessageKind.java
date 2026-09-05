package com.yapcore.regions;

import java.util.Locale;
import java.util.Optional;

/** String messages shown when entering / leaving a region or claim (not allow/deny flags). */
public enum RegionMessageKind {
    GREETING,
    FAREWELL;

    public static Optional<RegionMessageKind> parse(String raw) {
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

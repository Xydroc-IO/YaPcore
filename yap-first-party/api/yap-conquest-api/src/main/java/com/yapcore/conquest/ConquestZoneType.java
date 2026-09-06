package com.yapcore.conquest;

import java.util.Locale;
import java.util.Optional;

public enum ConquestZoneType {
    WILDERNESS,
    WARZONE,
    SAFEZONE;

    public static Optional<ConquestZoneType> parse(String raw) {
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

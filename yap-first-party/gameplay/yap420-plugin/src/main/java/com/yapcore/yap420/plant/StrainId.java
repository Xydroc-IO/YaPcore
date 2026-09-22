package com.yapcore.yap420.plant;

import java.util.Locale;
import java.util.Optional;

/** Supported plant strains. */
public enum StrainId {
    SATIVA,
    INDICA;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<StrainId> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(StrainId.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

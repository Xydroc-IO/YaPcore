package com.yapcore.regions;

import java.util.Locale;
import java.util.Optional;

/** Geometry kind for an admin region. */
public enum RegionShape {
    CUBOID,
    POLYGON;

    public static Optional<RegionShape> parse(String raw) {
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

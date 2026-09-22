package com.yapcore.yap420.market;

import java.util.Locale;
import java.util.Optional;

/** Retail weight / brick pack sizes. */
public enum PackUnit {
    GRAM,
    OUNCE,
    BRICK;

    public static Optional<PackUnit> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "g", "gram", "grams" -> Optional.of(GRAM);
            case "oz", "ounce", "ounces" -> Optional.of(OUNCE);
            case "lb", "lbs", "pound", "pounds", "brick", "bricks" -> Optional.of(BRICK);
            default -> Optional.empty();
        };
    }

    public String id() {
        return switch (this) {
            case GRAM -> "gram";
            case OUNCE -> "ounce";
            case BRICK -> "pound";
        };
    }
}

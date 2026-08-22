package com.yapcore.abilities;

public enum AbilityCategory {
    MAGIC,
    RANGED,
    MELEE,
    PRAYER,
    UTILITY;

    public static AbilityCategory parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MAGIC;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MAGIC;
        }
    }
}

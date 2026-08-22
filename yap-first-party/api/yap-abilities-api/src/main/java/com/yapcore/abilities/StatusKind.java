package com.yapcore.abilities;

public enum StatusKind {
    BUFF,
    DEBUFF;

    public static StatusKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return BUFF;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BUFF;
        }
    }
}

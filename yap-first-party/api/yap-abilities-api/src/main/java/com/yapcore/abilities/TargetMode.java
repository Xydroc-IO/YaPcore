package com.yapcore.abilities;

public enum TargetMode {
    RAYCAST,
    SELF,
    NONE,
    AREA,
    GROUND;

    public static TargetMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return RAYCAST;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return RAYCAST;
        }
    }
}

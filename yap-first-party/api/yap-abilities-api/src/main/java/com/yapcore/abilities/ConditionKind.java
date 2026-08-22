package com.yapcore.abilities;

public enum ConditionKind {
    HAS_STATUS,
    LACKS_STATUS,
    MIN_HP_PERCENT,
    MAX_HP_PERCENT,
    REQUIRES_MAINHAND,
    OFFHAND_EMPTY,
    ON_GROUND,
    IN_AIR;

    public static ConditionKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return HAS_STATUS;
        }
        try {
            return valueOf(raw.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return HAS_STATUS;
        }
    }
}

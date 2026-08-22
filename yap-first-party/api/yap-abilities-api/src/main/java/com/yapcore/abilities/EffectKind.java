package com.yapcore.abilities;

public enum EffectKind {
    DAMAGE,
    HEAL,
    VFX,
    SOUND,
    BUFF,
    DEBUFF,
    KNOCKBACK,
    DELAY,
    XP,
    DRAIN_PRAYER,
    TELEPORT,
    VELOCITY,
    AOE,
    ANIMATION,
    CHAIN,
    DISPLAY;

    public static EffectKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return VFX;
        }
        try {
            return valueOf(raw.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return VFX;
        }
    }
}

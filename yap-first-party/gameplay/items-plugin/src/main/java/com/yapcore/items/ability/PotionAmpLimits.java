package com.yapcore.items.ability;

import java.util.Locale;

/** Caps potion amplifiers that break movement / physics at high levels. */
final class PotionAmpLimits {

    /**
     * Max amplifier (0-based) for SPEED / JUMP / similar — level 2.
     * Level 5+ makes sprinting unplayable; vanilla "strong" potions are level 2.
     */
    private static final int MOVE_MAX = 1;

    private PotionAmpLimits() {
    }

    static int clamp(String effectName, int amplifier) {
        int amp = Math.max(0, Math.min(99, amplifier));
        if (effectName == null || effectName.isBlank()) {
            return amp;
        }
        String key = effectName.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if ("JUMP".equals(key)) {
            key = "JUMP_BOOST";
        }
        return switch (key) {
            case "SPEED", "JUMP_BOOST", "LEVITATION", "DOLPHINS_GRACE" -> Math.min(amp, MOVE_MAX);
            default -> amp;
        };
    }
}

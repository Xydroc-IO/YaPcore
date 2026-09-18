package com.yapcore.skills.power;

/**
 * Pre-strength-bonus damage for the combat XP listener on this region thread.
 * Set on the hit (HIGHEST) and taken on MONITOR so the damage bonus does not farm extra Strength XP.
 */
public final class SkillHitContext {

    private static final ThreadLocal<Double> TRAINING = new ThreadLocal<>();

    private SkillHitContext() {
    }

    public static void setTrainingDamage(double damage) {
        TRAINING.set(Math.max(0.0, damage));
    }

    public static double take(double fallback) {
        Double value = TRAINING.get();
        TRAINING.remove();
        if (value == null) {
            return fallback;
        }
        return value;
    }

    public static void clear() {
        TRAINING.remove();
    }
}

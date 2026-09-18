package com.yapcore.skills.power;

import java.util.Random;

/**
 * Level 1 is vanilla. The bonus grows linearly and reaches the configured cap at max level.
 * Gathering skills do not share a curve with Strength, and Strength does not speed breaks.
 */
public final class SkillPowerMath {

    private SkillPowerMath() {
    }

    /** 0 at level 1, 1 at {@code maxLevel}. */
    public static double progress(int level, int maxLevel) {
        int max = Math.max(2, maxLevel);
        int clamped = Math.max(1, Math.min(max, level));
        return (clamped - 1.0) / (max - 1.0);
    }

    /** Break-speed multiplier. 1.0 at level 1, {@code 1 + bonusAtMax} at the cap. */
    public static double breakSpeed(int level, int maxLevel, double bonusAtMax) {
        return 1.0 + progress(level, maxLevel) * Math.max(0.0, bonusAtMax);
    }

    /** Outgoing hit multiplier. 1.0 at level 1, {@code 1 + bonusAtMax} at the cap. */
    public static double damageMultiplier(int level, int maxLevel, double bonusAtMax) {
        return 1.0 + progress(level, maxLevel) * Math.max(0.0, bonusAtMax);
    }

    /** Expected extra copies of each vanilla drop (the rolled amount uses {@link #extraCopies}). */
    public static double expectedExtra(int level, int maxLevel, double extraAtMax) {
        return progress(level, maxLevel) * Math.max(0.0, extraAtMax);
    }

    /**
     * Extra full copies of each drop the block already produced.
     * Whole copies are guaranteed; the fraction is one more copy on {@code random}.
     */
    public static int extraCopies(int level, int maxLevel, double extraAtMax, Random random) {
        double raw = expectedExtra(level, maxLevel, extraAtMax);
        if (raw <= 0.0) {
            return 0;
        }
        int whole = (int) Math.floor(raw + 1.0e-9);
        double fraction = raw - whole;
        if (fraction > 1.0e-9 && random != null && random.nextDouble() < fraction) {
            whole++;
        }
        return Math.max(0, whole);
    }
}

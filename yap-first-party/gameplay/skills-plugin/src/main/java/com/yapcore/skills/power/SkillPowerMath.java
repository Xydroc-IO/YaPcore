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

    /** Walk-speed multiplier. 1.0 at level 1, {@code 1 + bonusAtMax} at the cap. */
    public static double moveSpeed(int level, int maxLevel, double bonusAtMax) {
        return 1.0 + progress(level, maxLevel) * Math.max(0.0, bonusAtMax);
    }

    /** Extra block-interaction range in blocks. 0 at level 1, {@code bonusAtMax} at the cap. */
    public static double placeReach(int level, int maxLevel, double bonusAtMax) {
        return progress(level, maxLevel) * Math.max(0.0, bonusAtMax);
    }

    /** Chance to keep the placed block. 0 at level 1, {@code chanceAtMax} (0–1) at the cap. */
    public static double keepBlockChance(int level, int maxLevel, double chanceAtMax) {
        return progress(level, maxLevel) * Math.max(0.0, Math.min(1.0, chanceAtMax));
    }

    public static boolean atMax(int level, int maxLevel) {
        return Math.max(1, level) >= Math.max(1, maxLevel);
    }

    /** Extra max-health points (2 HP = 1 heart). 0 at level 1, {@code bonusAtMax} at the cap. */
    public static double extraHearts(int level, int maxLevel, double bonusAtMax) {
        return progress(level, maxLevel) * Math.max(0.0, bonusAtMax);
    }

    /** Brew-speed multiplier. 1.0 at level 1, {@code 1 + bonusAtMax} at the cap. */
    public static double brewSpeed(int level, int maxLevel, double bonusAtMax) {
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

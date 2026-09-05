package com.yapcore.mmo;

/**
 * RuneScape-style cumulative XP curve (OSRS formula) with optional multiplier.
 * Level 1 requires 0 XP; YaPSkills defaults to max level 120.
 */
public final class XpTable {

    private final int maxLevel;
    private final double multiplier;
    private final int[] cumulativeXp;

    public XpTable(int maxLevel, double multiplier) {
        if (maxLevel < 2) {
            throw new IllegalArgumentException("maxLevel < 2");
        }
        if (multiplier <= 0) {
            throw new IllegalArgumentException("multiplier <= 0");
        }
        this.maxLevel = maxLevel;
        this.multiplier = multiplier;
        this.cumulativeXp = buildTable(maxLevel, multiplier);
    }

    public static XpTable runescape(int maxLevel, double multiplier) {
        return new XpTable(maxLevel, multiplier);
    }

    public int maxLevel() {
        return maxLevel;
    }

    public double multiplier() {
        return multiplier;
    }

    /** Total XP required to reach {@code level} (level 1 → 0). */
    public double xpForLevel(int level) {
        if (level <= 1) {
            return 0;
        }
        int idx = Math.min(level - 1, cumulativeXp.length - 1);
        return cumulativeXp[idx];
    }

    /** XP still needed from current total to reach {@code targetLevel}. */
    public double xpToLevel(double currentXp, int targetLevel) {
        return Math.max(0, xpForLevel(targetLevel) - currentXp);
    }

    /** Level for a cumulative XP total (1..maxLevel). */
    public int levelForXp(double xp) {
        if (xp <= 0) {
            return 1;
        }
        int lo = 1;
        int hi = maxLevel;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (xpForLevel(mid) <= xp) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** XP within the current level toward the next level. */
    public double xpIntoLevel(double totalXp, int level) {
        return totalXp - xpForLevel(level);
    }

    /** XP required to go from {@code level} to {@code level + 1}. */
    public double xpBetweenLevels(int level) {
        if (level >= maxLevel) {
            return 0;
        }
        return xpForLevel(level + 1) - xpForLevel(level);
    }

    private static int[] buildTable(int maxLevel, double multiplier) {
        int[] out = new int[maxLevel];
        out[0] = 0;
        for (int level = 2; level <= maxLevel; level++) {
            out[level - 1] = rawXpForLevel(level, multiplier);
        }
        return out;
    }

    /** Classic RS total XP to reach {@code level}. */
    static int rawXpForLevel(int level, double multiplier) {
        if (level <= 1) {
            return 0;
        }
        double points = 0;
        for (int lvl = 1; lvl < level; lvl++) {
            points += Math.floor(lvl + 300.0 * Math.pow(2.0, lvl / 7.0));
        }
        return (int) Math.floor(points / 4.0 * multiplier);
    }
}

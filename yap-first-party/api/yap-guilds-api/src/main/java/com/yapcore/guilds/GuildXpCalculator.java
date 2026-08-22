package com.yapcore.guilds;

/** Guild level math and perk scaling (unit tested). */
public final class GuildXpCalculator {

    public record Config(
            int maxLevel,
            long baseXpToLevel,
            double xpGrowth,
            int baseMaxMembers,
            int membersPerLevel,
            double baseBankCap,
            double bankCapPerLevel,
            double skillLevelUpGuildXp,
            double bossKillGuildXp) {

        public Config {
            if (maxLevel < 1) {
                maxLevel = 1;
            }
            if (baseXpToLevel < 1) {
                baseXpToLevel = 1;
            }
            if (xpGrowth < 1.0) {
                xpGrowth = 1.0;
            }
            if (baseMaxMembers < 1) {
                baseMaxMembers = 1;
            }
            if (membersPerLevel < 0) {
                membersPerLevel = 0;
            }
            if (baseBankCap < 0) {
                baseBankCap = 0;
            }
            if (bankCapPerLevel < 0) {
                bankCapPerLevel = 0;
            }
        }
    }

    public record LevelResult(int level, long xp, long xpForNext) {
    }

    private GuildXpCalculator() {
    }

    public static long xpForLevel(Config config, int level) {
        if (level <= 1) {
            return 0;
        }
        double total = 0;
        for (int lv = 2; lv <= level; lv++) {
            total += xpToAdvance(config, lv);
        }
        return Math.round(total);
    }

    public static long xpToAdvance(Config config, int targetLevel) {
        if (targetLevel <= 1) {
            return 0;
        }
        return Math.max(1L, Math.round(config.baseXpToLevel() * Math.pow(config.xpGrowth(), targetLevel - 2)));
    }

    public static LevelResult applyXp(Config config, int currentLevel, long currentXp, long addXp) {
        int level = Math.max(1, currentLevel);
        long xp = Math.max(0, currentXp) + Math.max(0, addXp);
        while (level < config.maxLevel()) {
            long needed = xpToAdvance(config, level + 1);
            if (xp < needed) {
                break;
            }
            xp -= needed;
            level++;
        }
        if (level >= config.maxLevel()) {
            xp = 0;
        }
        long next = level >= config.maxLevel() ? 0 : xpToAdvance(config, level + 1);
        return new LevelResult(level, xp, next);
    }

    public static int maxMembers(Config config, int level) {
        return config.baseMaxMembers() + Math.max(0, level - 1) * config.membersPerLevel();
    }

    public static double bankCap(Config config, int level) {
        return config.baseBankCap() + Math.max(0, level - 1) * config.bankCapPerLevel();
    }
}

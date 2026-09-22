package com.yapcore.skills.service;

import com.yapcore.mmo.SkillProgress;
import com.yapcore.mmo.XpTable;

/** One skill XP grant against an in-memory row. Caller persists under {@link SkillXpLocks}. */
public final class SkillXpGrant {

    private SkillXpGrant() {
    }

    public record Outcome(SkillProgress progress, int oldLevel) {
        public boolean leveled() {
            return progress.level() > oldLevel;
        }
    }

    public static Outcome apply(SkillProgress current, double amount, XpTable table) {
        int oldLevel = current.level();
        if (amount <= 0 || oldLevel >= table.maxLevel()) {
            return new Outcome(current, oldLevel);
        }
        double newXp = current.xp() + amount;
        int newLevel = table.levelForXp(newXp);
        if (newLevel > table.maxLevel()) {
            newLevel = table.maxLevel();
            newXp = table.xpForLevel(newLevel);
        }
        return new Outcome(
                new SkillProgress(current.playerId(), current.skillId(), newXp, newLevel),
                oldLevel);
    }
}

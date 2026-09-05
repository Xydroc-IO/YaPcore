package com.yapcore.dungeons;

/**
 * Skill / overall requirements for a dungeon level.
 *
 * @param overallMin  YaPSkills overall level (0 = none)
 * @param miningMin   mining skill level (0 = none)
 * @param strengthMin strength skill level (0 = none)
 */
public record DungeonGate(int overallMin, int miningMin, int strengthMin) {

    public static final DungeonGate NONE = new DungeonGate(0, 0, 0);

    public boolean hasRequirements() {
        return overallMin > 0 || miningMin > 0 || strengthMin > 0;
    }
}

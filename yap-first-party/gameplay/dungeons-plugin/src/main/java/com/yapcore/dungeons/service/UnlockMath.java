package com.yapcore.dungeons.service;

import com.yapcore.dungeons.DungeonProgress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure unlock / selectable-level rules (unit-testable). */
public final class UnlockMath {

    public static final int CORE_MAX = 50;
    public static final int PRESTIGE_MAX = 100;
    public static final int PRESTIGE_MIN = 51;
    public static final int OVERALL_ENTRY_MIN = 10;

    private UnlockMath() {
    }

    public static List<Integer> selectableLevels(DungeonProgress progress, int overallLevel) {
        if (overallLevel < OVERALL_ENTRY_MIN) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        int highest = Math.max(0, progress.highestCleared());
        int coreCap = Math.min(CORE_MAX, highest + 1);
        for (int i = 1; i <= coreCap; i++) {
            out.add(i);
        }
        if (highest >= CORE_MAX) {
            int prestigeCleared = progress.prestigeCleared();
            int prestigeCap = prestigeCleared < PRESTIGE_MIN
                    ? PRESTIGE_MIN
                    : Math.min(PRESTIGE_MAX, prestigeCleared + 1);
            for (int i = PRESTIGE_MIN; i <= prestigeCap; i++) {
                out.add(i);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static boolean canSelect(DungeonProgress progress, int overallLevel, int level) {
        return selectableLevels(progress, overallLevel).contains(level);
    }

    public static DungeonProgress afterClear(DungeonProgress before, int clearedLevel) {
        int highest = before.highestCleared();
        int prestige = before.prestigeCleared();
        if (clearedLevel >= 1 && clearedLevel <= CORE_MAX) {
            highest = Math.max(highest, clearedLevel);
        }
        if (clearedLevel >= PRESTIGE_MIN && clearedLevel <= PRESTIGE_MAX) {
            prestige = Math.max(prestige, clearedLevel);
            highest = Math.max(highest, CORE_MAX);
        }
        return new DungeonProgress(before.playerId(), highest, prestige, before.totalCompletions() + 1);
    }

    public static int maxLives(int partySize, int baseLives, int perExtra, int cap) {
        int size = Math.max(1, partySize);
        int lives = baseLives + Math.max(0, size - 1) * perExtra;
        return Math.min(cap, Math.max(1, lives));
    }

    public static String worldNameFor(String runId) {
        String shortId = runId == null ? "x" : runId.replace("-", "");
        if (shortId.length() > 12) {
            shortId = shortId.substring(0, 12);
        }
        return "yd_" + shortId.toLowerCase();
    }
}

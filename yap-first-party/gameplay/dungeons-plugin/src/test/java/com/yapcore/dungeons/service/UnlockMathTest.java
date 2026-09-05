package com.yapcore.dungeons.service;

import com.yapcore.dungeons.DungeonProgress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnlockMathTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void overallBelowTenLocksAll() {
        DungeonProgress p = DungeonProgress.empty(id);
        assertTrue(UnlockMath.selectableLevels(p, 9).isEmpty());
    }

    @Test
    void freshPlayerGetsLevelOne() {
        List<Integer> levels = UnlockMath.selectableLevels(DungeonProgress.empty(id), 10);
        assertEquals(List.of(1), levels);
    }

    @Test
    void clearingUnlocksNextCore() {
        DungeonProgress p = new DungeonProgress(id, 5, 0, 5);
        List<Integer> levels = UnlockMath.selectableLevels(p, 20);
        assertTrue(levels.contains(1));
        assertTrue(levels.contains(6));
        assertFalse(levels.contains(7));
    }

    @Test
    void clearFiftyUnlocksPrestigeFiftyOne() {
        DungeonProgress p = new DungeonProgress(id, 50, 0, 50);
        List<Integer> levels = UnlockMath.selectableLevels(p, 100);
        assertTrue(levels.contains(50));
        assertTrue(levels.contains(51));
        assertFalse(levels.contains(52));
    }

    @Test
    void afterClearUpdatesProgress() {
        DungeonProgress before = DungeonProgress.empty(id);
        DungeonProgress after = UnlockMath.afterClear(before, 1);
        assertEquals(1, after.highestCleared());
        assertEquals(1, after.totalCompletions());
    }

    @Test
    void prestigeClearTracked() {
        DungeonProgress before = new DungeonProgress(id, 50, 0, 50);
        DungeonProgress after = UnlockMath.afterClear(before, 51);
        assertEquals(50, after.highestCleared());
        assertEquals(51, after.prestigeCleared());
    }

    @Test
    void livesScaleWithParty() {
        assertEquals(3, UnlockMath.maxLives(1, 3, 1, 6));
        assertEquals(5, UnlockMath.maxLives(3, 3, 1, 6));
        assertEquals(6, UnlockMath.maxLives(8, 3, 1, 6));
    }

    @Test
    void worldNameSanitized() {
        String name = UnlockMath.worldNameFor("abcdef12-3456-7890");
        assertTrue(name.startsWith("yd_"));
        assertTrue(name.length() <= 16);
    }
}

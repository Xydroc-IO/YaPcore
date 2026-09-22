package com.yapcore.lagguard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemClearPolicyTest {

    @Test
    void emptyAllowMeansAllWorlds() {
        assertTrue(ItemClearPolicy.worldAllowed("world", List.of(), List.of()));
        assertFalse(ItemClearPolicy.worldAllowed("world_nether", List.of(), List.of("world_nether")));
    }

    @Test
    void allowListRestricts() {
        assertTrue(ItemClearPolicy.worldAllowed("world", List.of("world"), List.of()));
        assertFalse(ItemClearPolicy.worldAllowed("world_nether", List.of("world"), List.of()));
    }

    @Test
    void warnMatchesExactSeconds() {
        List<Integer> warns = List.of(60, 30, 10);
        assertTrue(ItemClearPolicy.shouldWarnAt(60, warns));
        assertTrue(ItemClearPolicy.shouldWarnAt(10, warns));
        assertFalse(ItemClearPolicy.shouldWarnAt(59, warns));
        assertFalse(ItemClearPolicy.shouldWarnAt(0, warns));
    }

    @Test
    void normalizeWarnSecondsDedupesAndSortsDesc() {
        assertEquals(List.of(60, 30, 10), ItemClearPolicy.normalizeWarnSeconds(List.of(10, 60, 30, 60, -1)));
        assertEquals(List.of(60, 30, 10), ItemClearPolicy.normalizeWarnSeconds(List.of()));
    }

    @Test
    void formatReplacesTokens() {
        assertEquals("in 30s — 12 / 3",
                ItemClearPolicy.format("in {seconds}s — {items} / {xp}", 30, 12, 3));
    }

    @Test
    void oldEnoughRespectsMinAge() {
        assertTrue(ItemClearPolicy.oldEnough(40, 40));
        assertFalse(ItemClearPolicy.oldEnough(39, 40));
        assertTrue(ItemClearPolicy.oldEnough(1, 0));
    }
}

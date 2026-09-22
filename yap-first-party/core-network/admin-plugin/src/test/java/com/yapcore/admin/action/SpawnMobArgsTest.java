package com.yapcore.admin.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SpawnMobArgsTest {

    @Test
    void defaults() {
        SpawnMobArgs a = SpawnMobArgs.parse(new String[]{});
        assertNull(a.error);
        assertEquals(1, a.amount);
        assertNull(a.level);
        assertNull(a.playerName);
    }

    @Test
    void amountAndLevel() {
        SpawnMobArgs a = SpawnMobArgs.parse(new String[]{"3", "50"});
        assertNull(a.error);
        assertEquals(3, a.amount);
        assertEquals(50, a.level);
    }

    @Test
    void levelKeyword() {
        SpawnMobArgs a = SpawnMobArgs.parse(new String[]{"level", "25"});
        assertNull(a.error);
        assertEquals(1, a.amount);
        assertEquals(25, a.level);
    }

    @Test
    void amountLevelPlayer() {
        SpawnMobArgs a = SpawnMobArgs.parse(new String[]{"2", "80", "Steve"});
        assertNull(a.error);
        assertEquals(2, a.amount);
        assertEquals(80, a.level);
        assertEquals("Steve", a.playerName);
    }

    @Test
    void amountThenLevelKeywordThenPlayer() {
        SpawnMobArgs a = SpawnMobArgs.parse(new String[]{"4", "lvl", "12", "Alex"});
        assertNull(a.error);
        assertEquals(4, a.amount);
        assertEquals(12, a.level);
        assertEquals("Alex", a.playerName);
    }

    @Test
    void clampsAmount() {
        assertEquals(64, SpawnMobArgs.parse(new String[]{"999"}).amount);
    }
}

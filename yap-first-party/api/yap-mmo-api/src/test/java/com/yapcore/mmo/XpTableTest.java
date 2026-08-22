package com.yapcore.mmo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XpTableTest {

    @Test
    void levelOneIsZeroXp() {
        XpTable table = XpTable.runescape(99, 1.0);
        assertEquals(0, table.xpForLevel(1));
        assertEquals(1, table.levelForXp(0));
    }

    @Test
    void knownRsLevelTwo() {
        XpTable table = XpTable.runescape(99, 1.0);
        assertEquals(83, table.xpForLevel(2));
        assertEquals(2, table.levelForXp(83));
    }

    @Test
    void levelNinetyNineBoundary() {
        XpTable table = XpTable.runescape(99, 1.0);
        int xp99 = (int) table.xpForLevel(99);
        assertTrue(xp99 > 12_000_000);
        assertEquals(99, table.levelForXp(xp99));
        assertEquals(99, table.levelForXp(xp99 + 1_000_000));
    }

    @Test
    void multiplierScalesCurve() {
        XpTable base = XpTable.runescape(99, 1.0);
        XpTable doubled = XpTable.runescape(99, 2.0);
        assertEquals(base.xpForLevel(50) * 2, doubled.xpForLevel(50), 1.0);
    }
}

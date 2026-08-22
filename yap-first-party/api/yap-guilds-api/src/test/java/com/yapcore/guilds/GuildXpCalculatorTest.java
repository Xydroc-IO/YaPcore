package com.yapcore.guilds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuildXpCalculatorTest {

    private static final GuildXpCalculator.Config CFG =
            new GuildXpCalculator.Config(50, 1000, 1.15, 5, 1, 10_000, 5000, 25, 500);

    @Test
    void maxMembersScalesWithLevel() {
        assertEquals(5, GuildXpCalculator.maxMembers(CFG, 1));
        assertEquals(10, GuildXpCalculator.maxMembers(CFG, 6));
    }

    @Test
    void bankCapScalesWithLevel() {
        assertEquals(10_000, GuildXpCalculator.bankCap(CFG, 1), 0.01);
        assertEquals(35_000, GuildXpCalculator.bankCap(CFG, 6), 0.01);
    }

    @Test
    void applyXpLevelsUp() {
        var result = GuildXpCalculator.applyXp(CFG, 1, 0, 1000);
        assertEquals(2, result.level());
        assertEquals(0, result.xp());
    }

    @Test
    void relationKeyOrdersIds() {
        var pair = GuildRelationKey.of(9, 3);
        assertEquals(3, pair.lowId());
        assertEquals(9, pair.highId());
        assertThrows(IllegalArgumentException.class, () -> GuildRelationKey.of(4, 4));
    }
}

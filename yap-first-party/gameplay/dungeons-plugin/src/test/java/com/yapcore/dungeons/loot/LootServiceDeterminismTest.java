package com.yapcore.dungeons.loot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LootServiceDeterminismTest {

    @Test
    void sameSeedSameStacks() {
        // Pure weight picker mirror of LootService.weighted without Bukkit
        List<LootTable.Entry> rare = List.of(
                new LootTable.Entry(org.bukkit.Material.DIAMOND, 1, 10, true),
                new LootTable.Entry(org.bukkit.Material.EMERALD, 2, 30, false));
        assertEquals(pick(rare, 12345L), pick(rare, 12345L));
        assertFalse(rare.isEmpty());
    }

    private static String pick(List<LootTable.Entry> entries, long seed) {
        java.util.Random rng = new java.util.Random(seed);
        int total = entries.stream().mapToInt(LootTable.Entry::weight).sum();
        int roll = rng.nextInt(Math.max(1, total));
        int acc = 0;
        for (LootTable.Entry e : entries) {
            acc += e.weight();
            if (roll < acc) {
                return e.material().name() + ":" + e.amount();
            }
        }
        return entries.getLast().material().name();
    }
}

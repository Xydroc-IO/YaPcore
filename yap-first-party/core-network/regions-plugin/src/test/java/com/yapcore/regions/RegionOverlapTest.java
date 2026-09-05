package com.yapcore.regions;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionOverlapTest {

    @Test
    void flagParseAcceptsHyphenAndUnderscore() {
        assertEquals(Optional.of(RegionFlag.CHEST_ACCESS), RegionFlag.parse("chest-access"));
        assertEquals(Optional.of(RegionFlag.CREEPER_EXPLOSION), RegionFlag.parse("creeper_explosion"));
        assertTrue(RegionFlag.parse("not-a-flag").isEmpty());
    }

    @Test
    void smallestVolumeWinsWhenPriorityTied() {
        AdminRegion outer = region(1, "outer", 0, 0, 100, 0, 100, 0, 100, Map.of());
        AdminRegion inner = region(2, "inner", 0, 40, 60, 40, 60, 40, 60,
                Map.of(RegionFlag.PVP, FlagValue.DENY));
        AdminRegion found = RegionLookup.at(List.of(outer, inner), "world", 50, 50, 50).orElseThrow();
        assertEquals("inner", found.name());
        assertEquals(FlagValue.DENY, found.flags().get(RegionFlag.PVP));
    }

    @Test
    void higherPriorityBeatsSmallerVolume() {
        AdminRegion small = region(1, "small", 0, 40, 60, 40, 60, 40, 60, Map.of());
        AdminRegion large = region(2, "large", 10, 0, 100, 0, 100, 0, 100,
                Map.of(RegionFlag.BUILD, FlagValue.DENY));
        AdminRegion found = RegionLookup.at(List.of(small, large), "world", 50, 50, 50).orElseThrow();
        assertEquals("large", found.name());
        assertEquals(10, found.priority());
        assertEquals(FlagValue.DENY, found.flags().get(RegionFlag.BUILD));
    }

    @Test
    void equalPriorityFallsBackToSmallestVolume() {
        AdminRegion outer = region(1, "outer", 5, 0, 100, 0, 100, 0, 100, Map.of());
        AdminRegion inner = region(2, "inner", 5, 40, 60, 40, 60, 40, 60,
                Map.of(RegionFlag.ENTRY, FlagValue.DENY));
        AdminRegion found = RegionLookup.at(List.of(outer, inner), "world", 50, 50, 50).orElseThrow();
        assertEquals("inner", found.name());
        assertEquals(5, found.priority());
    }

    @Test
    void defaultPriorityZeroUsesVolumeHeuristic() {
        AdminRegion a = region(1, "a", 0, 0, 50, 0, 50, 0, 50, Map.of());
        AdminRegion b = region(2, "b", 0, 0, 10, 0, 10, 0, 10, Map.of());
        AdminRegion found = RegionLookup.at(List.of(a, b), "world", 5, 5, 5).orElseThrow();
        assertEquals("b", found.name());
    }

    private static AdminRegion region(long id, String name, int priority,
                                      int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                      Map<RegionFlag, FlagValue> flags) {
        return new AdminRegion(id, "default", "world", minX, maxX, minY, maxY, minZ, maxZ, name, priority,
                flags.isEmpty() ? Map.of() : new EnumMap<>(flags));
    }
}

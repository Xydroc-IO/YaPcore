package com.yapcore.regions;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolygonContainmentTest {

    @Test
    void pointInTriangle() {
        List<RegionVertex> tri = List.of(
                new RegionVertex(0, 0),
                new RegionVertex(10, 0),
                new RegionVertex(0, 10));
        assertTrue(Polygons.contains(tri, 2, 2));
        assertFalse(Polygons.contains(tri, 9, 9));
        assertFalse(Polygons.contains(tri, -1, 0));
    }

    @Test
    void polygonRegionRespectsYAndPip() {
        AdminRegion poly = new AdminRegion(
                1, "default", "world",
                0, 10, 40, 60, 0, 10,
                "arena", 0, Map.of(),
                RegionShape.POLYGON,
                List.of(new RegionVertex(0, 0), new RegionVertex(10, 0), new RegionVertex(0, 10)));
        assertTrue(poly.contains("world", 2, 50, 2));
        assertFalse(poly.contains("world", 2, 10, 2)); // below y
        assertFalse(poly.contains("world", 9, 50, 9)); // outside triangle
        assertFalse(poly.contains("other", 2, 50, 2));
    }

    @Test
    void polygonOverlapUsesPriorityThenVolume() {
        AdminRegion outer = new AdminRegion(
                1, "default", "world",
                0, 20, 0, 100, 0, 20,
                "outer", 0, Map.of(),
                RegionShape.POLYGON,
                List.of(new RegionVertex(0, 0), new RegionVertex(20, 0),
                        new RegionVertex(20, 20), new RegionVertex(0, 20)));
        AdminRegion inner = new AdminRegion(
                2, "default", "world",
                2, 8, 0, 100, 2, 8,
                "inner", 0,
                Map.of(RegionFlag.PVP, FlagValue.DENY),
                RegionShape.POLYGON,
                List.of(new RegionVertex(2, 2), new RegionVertex(8, 2),
                        new RegionVertex(8, 8), new RegionVertex(2, 8)));
        AdminRegion found = RegionLookup.at(List.of(outer, inner), "world", 5, 50, 5).orElseThrow();
        assertEquals("inner", found.name());

        AdminRegion raised = new AdminRegion(
                3, "default", "world",
                0, 20, 0, 100, 0, 20,
                "raised", 10,
                Map.of(RegionFlag.BUILD, FlagValue.DENY),
                RegionShape.POLYGON,
                List.of(new RegionVertex(0, 0), new RegionVertex(20, 0),
                        new RegionVertex(20, 20), new RegionVertex(0, 20)));
        AdminRegion winner = RegionLookup.at(List.of(inner, raised), "world", 5, 50, 5).orElseThrow();
        assertEquals("raised", winner.name());
    }

    @Test
    void cuboidStillWorksAlongsidePolygon() {
        AdminRegion cuboid = regionCuboid(1, "box", 0, 0, 5, 0, 5, 0, 5, Map.of());
        AdminRegion poly = new AdminRegion(
                2, "default", "world",
                0, 10, 0, 5, 0, 10,
                "poly", 5, Map.of(RegionFlag.ENTRY, FlagValue.DENY),
                RegionShape.POLYGON,
                List.of(new RegionVertex(0, 0), new RegionVertex(10, 0), new RegionVertex(0, 10)));
        AdminRegion found = RegionLookup.at(List.of(cuboid, poly), "world", 2, 2, 2).orElseThrow();
        assertEquals("poly", found.name());
    }

    private static AdminRegion regionCuboid(long id, String name, int priority,
                                            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                            Map<RegionFlag, FlagValue> flags) {
        return new AdminRegion(id, "default", "world", minX, maxX, minY, maxY, minZ, maxZ, name, priority,
                flags.isEmpty() ? Map.of() : new EnumMap<>(flags));
    }
}

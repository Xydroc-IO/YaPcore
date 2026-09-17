package com.yapcore.regions;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Staff admin region. Cuboids use AABB only; polygons use ordered XZ vertices plus Y range.
 * Bounding min/max always describe the axis-aligned envelope (used for volume ties and JSON).
 * Optional {@code gameMode} forces survival/creative/adventure/spectator while inside.
 */
public record AdminRegion(
        long id,
        String serverId,
        String world,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        String name,
        int priority,
        Map<RegionFlag, FlagValue> flags,
        RegionShape shape,
        List<RegionVertex> vertices,
        String gameMode
) {
    public AdminRegion {
        shape = shape == null ? RegionShape.CUBOID : shape;
        vertices = vertices == null || vertices.isEmpty() ? List.of() : List.copyOf(vertices);
        flags = flags == null ? Map.of() : Map.copyOf(flags);
        if (gameMode != null && gameMode.isBlank()) {
            gameMode = null;
        } else if (gameMode != null) {
            gameMode = gameMode.trim().toLowerCase(Locale.ROOT);
        }
    }

    /** Cuboid without gamemode (empty vertices). */
    public AdminRegion(
            long id,
            String serverId,
            String world,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            String name,
            int priority,
            Map<RegionFlag, FlagValue> flags
    ) {
        this(id, serverId, world, minX, maxX, minY, maxY, minZ, maxZ, name, priority, flags,
                RegionShape.CUBOID, List.of(), null);
    }

    /** Shape + vertices without gamemode. */
    public AdminRegion(
            long id,
            String serverId,
            String world,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            String name,
            int priority,
            Map<RegionFlag, FlagValue> flags,
            RegionShape shape,
            List<RegionVertex> vertices
    ) {
        this(id, serverId, world, minX, maxX, minY, maxY, minZ, maxZ, name, priority, flags,
                shape, vertices, null);
    }

    public boolean contains(String w, int x, int y, int z) {
        if (!world.equals(w) || y < minY || y > maxY) {
            return false;
        }
        if (shape == RegionShape.POLYGON && !vertices.isEmpty()) {
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                return false;
            }
            return Polygons.contains(vertices, x, z);
        }
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * Inclusive volume heuristic for overlap ties.
     * Cuboid: full AABB. Polygon: shoelace area × Y span (floored to ≥ 1).
     */
    public long volume() {
        long dy = (long) maxY - minY + 1;
        if (shape == RegionShape.POLYGON && vertices.size() >= 3) {
            long prism = Math.max(1L, Math.round(Polygons.area(vertices) * dy));
            return prism;
        }
        long dx = (long) maxX - minX + 1;
        long dz = (long) maxZ - minZ + 1;
        return dx * dy * dz;
    }

    public boolean isPolygon() {
        return shape == RegionShape.POLYGON && vertices.size() >= 3;
    }

    public boolean hasGameMode() {
        return gameMode != null && !gameMode.isBlank();
    }
}

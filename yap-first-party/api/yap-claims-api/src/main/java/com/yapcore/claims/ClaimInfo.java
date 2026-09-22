package com.yapcore.claims;

import java.util.UUID;

/**
 * Read-only claim summary for integrators (regen, disasters, map, …).
 * Bounds are inclusive XZ rectangles (full height).
 */
public record ClaimInfo(
        long id,
        UUID owner,
        String serverId,
        String world,
        int minX,
        int maxX,
        int minZ,
        int maxZ,
        String name,
        Long parentId
) {
    public boolean isSubdivision() {
        return parentId != null;
    }

    public boolean contains(String worldName, int x, int z) {
        return world.equals(worldName) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}

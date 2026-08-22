package com.yapcore.regions;

import java.util.Map;

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
        Map<RegionFlag, FlagValue> flags
) {
    public boolean contains(String w, int x, int y, int z) {
        return world.equals(w) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}

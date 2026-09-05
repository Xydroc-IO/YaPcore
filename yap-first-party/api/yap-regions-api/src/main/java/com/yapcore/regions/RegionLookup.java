package com.yapcore.regions;

import java.util.List;
import java.util.Optional;

/**
 * Pure overlap resolution for admin regions (no Bukkit).
 * Highest {@link AdminRegion#priority()} wins; ties break by smallest volume.
 * Scans all candidates (O(n)) — fine for typical admin-region counts; no spatial index.
 */
public final class RegionLookup {

    private RegionLookup() {
    }

    public static Optional<AdminRegion> at(List<AdminRegion> regions, String world, int x, int y, int z) {
        if (regions == null || regions.isEmpty() || world == null) {
            return Optional.empty();
        }
        AdminRegion found = null;
        int bestPriority = Integer.MIN_VALUE;
        long bestVolume = Long.MAX_VALUE;
        for (AdminRegion region : regions) {
            if (!region.contains(world, x, y, z)) {
                continue;
            }
            int priority = region.priority();
            long volume = region.volume();
            if (found == null
                    || priority > bestPriority
                    || (priority == bestPriority && volume < bestVolume)) {
                found = region;
                bestPriority = priority;
                bestVolume = volume;
            }
        }
        return Optional.ofNullable(found);
    }
}

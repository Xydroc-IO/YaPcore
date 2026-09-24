package com.yapcore.yapblock.level;

import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.grid.IslandIndex;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Cached top-N islands by level. */
public final class IslandTopCache {

    private final IslandIndex index;
    private final AtomicReference<List<IslandSnapshot>> cache = new AtomicReference<>(List.of());
    private volatile int cachedLimit = -1;

    public IslandTopCache(IslandIndex index) {
        this.index = index;
    }

    public List<IslandSnapshot> top(int limit) {
        int n = Math.max(1, limit);
        List<IslandSnapshot> current = cache.get();
        if (cachedLimit == n && !current.isEmpty()) {
            return current;
        }
        List<IslandSnapshot> fresh = index.topByLevel(n);
        cache.set(fresh);
        cachedLimit = n;
        return fresh;
    }

    public void invalidate() {
        cachedLimit = -1;
        cache.set(List.of());
    }

    public int rankOf(long islandId) {
        List<IslandSnapshot> all = index.topByLevel(Integer.MAX_VALUE);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id() == islandId) {
                return i + 1;
            }
        }
        return 0;
    }
}

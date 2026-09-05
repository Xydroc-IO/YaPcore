package com.yapcore.regions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player pending polygon vertices for {@code /region definepoly}. */
public final class PolyDraftService {

    private final Map<UUID, List<RegionVertex>> drafts = new ConcurrentHashMap<>();

    public void add(UUID player, int x, int z) {
        drafts.computeIfAbsent(player, id -> new ArrayList<>()).add(new RegionVertex(x, z));
    }

    public void clear(UUID player) {
        drafts.remove(player);
    }

    public List<RegionVertex> points(UUID player) {
        List<RegionVertex> pts = drafts.get(player);
        if (pts == null || pts.isEmpty()) {
            return List.of();
        }
        return List.copyOf(pts);
    }

    public int size(UUID player) {
        List<RegionVertex> pts = drafts.get(player);
        return pts == null ? 0 : pts.size();
    }
}

package com.yapcore.yapblock.service;

import com.yapcore.yapblock.IslandRole;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory player→island role cache. */
public final class IslandRoleCache {

    private final Map<Long, Map<UUID, IslandRole>> byIsland = new ConcurrentHashMap<>();

    public void put(long islandId, UUID playerId, IslandRole role) {
        byIsland.computeIfAbsent(islandId, id -> new ConcurrentHashMap<>()).put(playerId, role);
    }

    public void remove(long islandId, UUID playerId) {
        Map<UUID, IslandRole> map = byIsland.get(islandId);
        if (map != null) {
            map.remove(playerId);
        }
    }

    public void clearIsland(long islandId) {
        byIsland.remove(islandId);
    }

    public void clear() {
        byIsland.clear();
    }

    public Optional<IslandRole> role(UUID playerId, long islandId) {
        Map<UUID, IslandRole> map = byIsland.get(islandId);
        if (map == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(playerId));
    }

    public int countNonBanned(long islandId) {
        Map<UUID, IslandRole> map = byIsland.get(islandId);
        if (map == null) {
            return 0;
        }
        int n = 0;
        for (IslandRole role : map.values()) {
            if (role != IslandRole.BANNED) {
                n++;
            }
        }
        return n;
    }
}

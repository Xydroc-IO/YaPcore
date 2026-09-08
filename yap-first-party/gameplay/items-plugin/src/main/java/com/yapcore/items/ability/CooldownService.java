package com.yapcore.items.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player per-item ability cooldowns. */
public final class CooldownService {

    private final Map<UUID, Map<String, Long>> until = new ConcurrentHashMap<>();

    public boolean ready(UUID playerId, String itemId) {
        Map<String, Long> map = until.get(playerId);
        if (map == null) {
            return true;
        }
        Long end = map.get(itemId);
        return end == null || System.currentTimeMillis() >= end;
    }

    public long remainingMs(UUID playerId, String itemId) {
        Map<String, Long> map = until.get(playerId);
        if (map == null) {
            return 0L;
        }
        Long end = map.get(itemId);
        if (end == null) {
            return 0L;
        }
        return Math.max(0L, end - System.currentTimeMillis());
    }

    public void set(UUID playerId, String itemId, long cooldownMs) {
        if (cooldownMs <= 0L) {
            return;
        }
        until.computeIfAbsent(playerId, u -> new ConcurrentHashMap<>())
                .put(itemId, System.currentTimeMillis() + cooldownMs);
    }

    public void clear(UUID playerId) {
        until.remove(playerId);
    }
}

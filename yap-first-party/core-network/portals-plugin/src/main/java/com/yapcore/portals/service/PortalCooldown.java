package com.yapcore.portals.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player portal cooldown (ms epoch). Thread-safe across region threads. */
public final class PortalCooldown {

    private final Map<UUID, Long> untilMs = new ConcurrentHashMap<>();

    public void clear(UUID uuid) {
        if (uuid != null) {
            untilMs.remove(uuid);
        }
    }

    public void clearAll() {
        untilMs.clear();
    }

    /**
     * @return remaining seconds (ceil), or 0 if ready
     */
    public int remainingSeconds(UUID uuid, long nowMs) {
        Long until = untilMs.get(uuid);
        if (until == null || until <= nowMs) {
            return 0;
        }
        long rem = until - nowMs;
        return (int) Math.max(1L, (rem + 999L) / 1000L);
    }

    public boolean ready(UUID uuid, long nowMs) {
        return remainingSeconds(uuid, nowMs) == 0;
    }

    public void mark(UUID uuid, int cooldownSeconds, long nowMs) {
        if (uuid == null || cooldownSeconds <= 0) {
            return;
        }
        untilMs.put(uuid, nowMs + cooldownSeconds * 1000L);
    }
}

package com.yapcore.link;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived /server redirect tokens (UUID → backend name). */
public final class RedirectTokens {

    private record Entry(String server, long expiresAtMs) {
    }

    private final Map<UUID, Entry> map = new ConcurrentHashMap<>();
    private final long ttlMs;

    public RedirectTokens(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public void put(UUID id, String server) {
        map.put(id, new Entry(server, System.currentTimeMillis() + ttlMs));
    }

    public String take(UUID id) {
        Entry e = map.remove(id);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() > e.expiresAtMs()) {
            return null;
        }
        return e.server();
    }
}

package com.yapcore.conquest;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory combat tags (unit-testable). */
public final class ConquestCombatTagTracker {

    private final Map<UUID, Instant> until = new ConcurrentHashMap<>();

    public void tag(UUID playerId, Instant expiresAt) {
        if (playerId == null || expiresAt == null) {
            return;
        }
        until.merge(playerId, expiresAt, (a, b) -> a.isAfter(b) ? a : b);
    }

    public void tag(UUID playerId, Instant now, int durationSeconds) {
        if (durationSeconds <= 0) {
            return;
        }
        tag(playerId, now.plusSeconds(durationSeconds));
    }

    public boolean isTagged(UUID playerId, Instant now) {
        Instant exp = until.get(playerId);
        if (exp == null) {
            return false;
        }
        if (!exp.isAfter(now)) {
            until.remove(playerId, exp);
            return false;
        }
        return true;
    }

    public Optional<Instant> expiresAt(UUID playerId) {
        return Optional.ofNullable(until.get(playerId));
    }

    public void clear(UUID playerId) {
        until.remove(playerId);
    }

    public void clearExpired(Instant now) {
        until.entrySet().removeIf(e -> !e.getValue().isAfter(now));
    }
}

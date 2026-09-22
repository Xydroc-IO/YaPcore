package com.yapcore.skills.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Serializes skill + overall XP writes per player so multi-block breaks cannot all
 * read level 1 and notify {@code 1 → 2} three times.
 */
public final class SkillXpLocks {

    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();

    public <T> T withPlayer(UUID playerId, Supplier<T> work) {
        Object lock = locks.computeIfAbsent(playerId, id -> new Object());
        synchronized (lock) {
            return work.get();
        }
    }
}

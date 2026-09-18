package com.yapcore.skills.power;

import com.yapcore.mmo.SkillId;
import com.yapcore.mmo.SkillProgress;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill levels readable on any region thread. Writers are the async DB path and join warmup.
 * A missing row is level 1. {@link #loaded(UUID)} is false until the full row set has been read.
 */
public final class SkillLevelCache {

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>> levels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap.KeySetView<UUID, Boolean> loaded = ConcurrentHashMap.newKeySet();

    public int level(UUID playerId, SkillId skillId) {
        if (playerId == null || skillId == null) {
            return 1;
        }
        ConcurrentHashMap<String, Integer> row = levels.get(playerId);
        if (row == null) {
            return 1;
        }
        return Math.max(1, row.getOrDefault(skillId.id(), 1));
    }

    public boolean loaded(UUID playerId) {
        return playerId != null && loaded.contains(playerId);
    }

    /** Authoritative write after XP or {@code /skill set}. Overwrites a stale warmup row. */
    public void remember(UUID playerId, SkillId skillId, int level) {
        if (playerId == null || skillId == null) {
            return;
        }
        levels.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .put(skillId.id(), Math.max(1, level));
    }

    /**
     * Join/reload fill. Does not overwrite a level already remembered by a newer XP write
     * that landed while this read was in flight.
     */
    public void rememberAll(UUID playerId, Collection<SkillProgress> progress) {
        if (playerId == null) {
            return;
        }
        ConcurrentHashMap<String, Integer> row = levels.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>());
        if (progress != null) {
            for (SkillProgress entry : progress) {
                if (entry == null || entry.skillId() == null) {
                    continue;
                }
                row.putIfAbsent(entry.skillId().id(), Math.max(1, entry.level()));
            }
        }
        loaded.add(playerId);
    }

    public void forget(UUID playerId) {
        if (playerId == null) {
            return;
        }
        levels.remove(playerId);
        loaded.remove(playerId);
    }

    public void clear() {
        levels.clear();
        loaded.clear();
    }
}

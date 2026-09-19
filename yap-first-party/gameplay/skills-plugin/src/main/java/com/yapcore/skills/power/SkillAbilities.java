package com.yapcore.skills.power;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Super Breaker / Tree Feller windows and cooldowns. */
public final class SkillAbilities {

    public enum Kind {
        SUPER_BREAKER("Super Breaker"),
        TREE_FELLER("Tree Feller");

        private final String display;

        Kind(String display) {
            this.display = display;
        }

        public String display() {
            return display;
        }
    }

    private final Map<UUID, EnumMap<Kind, Long>> activeUntil = new ConcurrentHashMap<>();
    private final Map<UUID, EnumMap<Kind, Long>> cooldownUntil = new ConcurrentHashMap<>();

    public boolean isActive(UUID playerId, Kind kind) {
        if (playerId == null || kind == null) {
            return false;
        }
        EnumMap<Kind, Long> row = activeUntil.get(playerId);
        if (row == null) {
            return false;
        }
        Long until = row.get(kind);
        return until != null && until > System.currentTimeMillis();
    }

    public long cooldownLeftMs(UUID playerId, Kind kind) {
        if (playerId == null || kind == null) {
            return 0L;
        }
        EnumMap<Kind, Long> row = cooldownUntil.get(playerId);
        if (row == null) {
            return 0L;
        }
        Long until = row.get(kind);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public boolean tryActivate(UUID playerId, Kind kind, int durationTicks, int cooldownTicks) {
        if (playerId == null || kind == null) {
            return false;
        }
        if (isActive(playerId, kind) || cooldownLeftMs(playerId, kind) > 0L) {
            return false;
        }
        long now = System.currentTimeMillis();
        long durationMs = Math.max(1, durationTicks) * 50L;
        long cooldownMs = durationMs + Math.max(1, cooldownTicks) * 50L;
        activeUntil.computeIfAbsent(playerId, id -> new EnumMap<>(Kind.class)).put(kind, now + durationMs);
        cooldownUntil.computeIfAbsent(playerId, id -> new EnumMap<>(Kind.class)).put(kind, now + cooldownMs);
        return true;
    }

    public void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        activeUntil.remove(playerId);
        cooldownUntil.remove(playerId);
    }
}

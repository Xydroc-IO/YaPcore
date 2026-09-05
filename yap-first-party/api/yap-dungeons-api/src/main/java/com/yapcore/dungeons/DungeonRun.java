package com.yapcore.dungeons;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Live or recent dungeon instance.
 *
 * @param runId       opaque id (also used in world name {@code yd_<short>})
 * @param dungeonLevel 1–100
 * @param worldName    Bukkit world name
 * @param seed         generation seed
 * @param lives        remaining shared party lives
 * @param maxLives     starting lives for this run
 */
public record DungeonRun(
        String runId,
        int dungeonLevel,
        UUID leader,
        Set<UUID> members,
        String worldName,
        long seed,
        DungeonRunState state,
        int lives,
        int maxLives,
        Instant startedAt,
        Instant endedAt) {

    public boolean isMember(UUID playerId) {
        return leader.equals(playerId) || members.contains(playerId);
    }

    public boolean isTerminal() {
        return state == DungeonRunState.CLEARED
                || state == DungeonRunState.FAILED
                || state == DungeonRunState.CLEANING;
    }

    public boolean isPrestige() {
        return dungeonLevel >= 51;
    }
}

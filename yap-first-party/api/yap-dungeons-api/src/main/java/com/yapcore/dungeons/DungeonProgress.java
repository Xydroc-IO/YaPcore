package com.yapcore.dungeons;

import java.util.UUID;

/**
 * Persistent ladder progress for a player.
 *
 * @param highestCleared   highest core level cleared (0 = none; max 50)
 * @param prestigeCleared  highest prestige level cleared (0 = none; 51–100 when set)
 */
public record DungeonProgress(
        UUID playerId,
        int highestCleared,
        int prestigeCleared,
        int totalCompletions) {

    public static DungeonProgress empty(UUID playerId) {
        return new DungeonProgress(playerId, 0, 0, 0);
    }

    public boolean prestigeUnlocked() {
        return highestCleared >= 50;
    }
}

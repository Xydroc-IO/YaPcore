package com.yapcore.games.mode;

import com.yapcore.games.GameModeId;

public record GameModeDefinition(
        GameModeId id,
        String displayName,
        GameModeType type,
        String arenaId,
        String kitId,
        int minPlayers,
        int maxPlayers,
        int countdownSeconds,
        int durationSeconds,
        int winKills,
        boolean respawnInArena) {
}

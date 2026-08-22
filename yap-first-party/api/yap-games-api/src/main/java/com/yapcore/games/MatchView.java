package com.yapcore.games;

import java.util.Set;
import java.util.UUID;

/** Read-only match summary for other plugins. */
public record MatchView(
        MatchId matchId,
        GameModeId mode,
        MatchState state,
        String arenaId,
        Set<UUID> players,
        int aliveCount) {
}

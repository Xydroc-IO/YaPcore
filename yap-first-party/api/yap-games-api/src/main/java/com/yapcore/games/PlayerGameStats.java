package com.yapcore.games;

import java.util.UUID;

/** Persistent minigame stats per mode. */
public record PlayerGameStats(UUID playerId, GameModeId mode, int wins, int kills, int deaths) {
}

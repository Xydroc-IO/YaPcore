package com.yapcore.mmo;

import java.util.UUID;

/** One row on a skill hiscore board. */
public record HiscoreEntry(int rank, UUID playerId, String playerName, int level, double xp) {
}

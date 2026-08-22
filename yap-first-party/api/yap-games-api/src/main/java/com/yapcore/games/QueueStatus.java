package com.yapcore.games;

/** Queue position for a waiting player. */
public record QueueStatus(GameModeId mode, int position, int queueSize) {
}

package com.yapcore.mmo;

import java.util.UUID;

/** First-class player overall progression (separate from per-skill XP). */
public record PlayerOverall(UUID playerId, double xp, int level) {

    public PlayerOverall {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId");
        }
        if (xp < 0) {
            xp = 0;
        }
        if (level < 1) {
            level = 1;
        }
    }

    public static PlayerOverall fresh(UUID playerId) {
        return new PlayerOverall(playerId, 0, 1);
    }
}

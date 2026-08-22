package com.yapcore.factions;

import java.time.Instant;
import java.util.UUID;

public record Faction(
        long id,
        String name,
        String tag,
        UUID leaderId,
        int power,
        int maxPower,
        String description,
        String motd,
        FactionJoinMode joinMode,
        double bankBalance,
        FactionHome home,
        Instant shieldUntil,
        Instant createdAt) {

    public boolean isShielded() {
        return shieldUntil != null && shieldUntil.isAfter(Instant.now());
    }
}

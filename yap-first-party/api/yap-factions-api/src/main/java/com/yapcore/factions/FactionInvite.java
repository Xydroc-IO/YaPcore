package com.yapcore.factions;

import java.time.Instant;
import java.util.UUID;

public record FactionInvite(
        long factionId,
        UUID playerId,
        UUID invitedBy,
        Instant createdAt,
        Instant expiresAt) {

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}

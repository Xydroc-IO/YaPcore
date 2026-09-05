package com.yapcore.dungeons;

import java.time.Instant;
import java.util.UUID;

public record DungeonInvite(
        String runId,
        UUID invitee,
        UUID invitedBy,
        Instant createdAt,
        Instant expiresAt) {

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}

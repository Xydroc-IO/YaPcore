package com.yapcore.guilds;

import java.time.Instant;
import java.util.UUID;

public record GuildInvite(
        long guildId,
        UUID playerId,
        UUID invitedBy,
        Instant createdAt,
        Instant expiresAt) {

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}

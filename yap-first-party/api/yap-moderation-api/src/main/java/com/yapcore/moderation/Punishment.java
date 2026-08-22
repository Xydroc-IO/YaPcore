package com.yapcore.moderation;

import java.util.UUID;

public record Punishment(
        long id,
        PunishmentType type,
        UUID targetUuid,
        String targetName,
        UUID actorUuid,
        String actorName,
        String reason,
        String ipAddress,
        long createdAtEpochMs,
        long expiresAtEpochMs,
        boolean active
) {
    public boolean isPermanent() {
        return expiresAtEpochMs <= 0L;
    }

    public boolean isExpired(long nowEpochMs) {
        return !isPermanent() && expiresAtEpochMs <= nowEpochMs;
    }
}

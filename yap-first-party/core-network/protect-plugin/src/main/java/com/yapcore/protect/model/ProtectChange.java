package com.yapcore.protect.model;

import java.util.UUID;

public record ProtectChange(
        long id,
        ChangeType changeType,
        UUID actorUuid,
        String actorName,
        String world,
        int x,
        int y,
        int z,
        String blockBefore,
        String blockAfter,
        long epochMs,
        boolean rolledBack
) {
}

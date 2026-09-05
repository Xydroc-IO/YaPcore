package com.yapcore.protect;

import java.util.UUID;

public record BlockChangeRecord(
        long id,
        String serverId,
        UUID actorUuid,
        String actorName,
        String world,
        int x,
        int y,
        int z,
        String changeType,
        String blockBefore,
        String blockAfter,
        long epochMs,
        boolean rolledBack
) {
    /** Lookup-only types that cannot be rolled back/restored (entity kill, container open). */
    public boolean restorable() {
        return "BLOCK_BREAK".equals(changeType)
                || "BLOCK_PLACE".equals(changeType)
                || "CONTAINER_INVENTORY".equals(changeType)
                || "EXPLOSION".equals(changeType)
                || "LIQUID_FLOW".equals(changeType)
                || "FIRE".equals(changeType);
    }
}

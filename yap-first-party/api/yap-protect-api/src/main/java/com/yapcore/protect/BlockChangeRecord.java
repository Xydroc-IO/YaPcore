package com.yapcore.protect;

import java.util.UUID;

public record BlockChangeRecord(
        long id,
        UUID actorUuid,
        String actorName,
        String world,
        int x,
        int y,
        int z,
        String changeType,
        String blockBefore,
        String blockAfter,
        long epochMs
) {
}

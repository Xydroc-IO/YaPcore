package com.yapcore.npcs;

import java.util.UUID;

public record QuestProgress(
        UUID playerUuid,
        String questId,
        String objectiveId,
        int progress,
        int required,
        boolean completed
) {
}

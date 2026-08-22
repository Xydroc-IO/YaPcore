package com.yapcore.mmo;

import java.util.UUID;

public record SkillProgress(UUID playerId, SkillId skillId, double xp, int level) {

    public SkillProgress {
        if (level < 1) {
            throw new IllegalArgumentException("level < 1");
        }
        if (xp < 0) {
            throw new IllegalArgumentException("xp < 0");
        }
    }
}

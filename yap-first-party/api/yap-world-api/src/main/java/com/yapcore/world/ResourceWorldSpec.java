package com.yapcore.world;

import java.util.List;

/** Options for a scheduled / admin resource-world wipe. */
public record ResourceWorldSpec(
        String name,
        long resetIntervalHours,
        List<Integer> announceMinutes,
        String environment,
        String type,
        Long seed,
        boolean generateStructures,
        boolean denyClaims
) {
    public WorldCreateOptions createOptions() {
        return new WorldCreateOptions(
                type == null || type.isBlank() ? "NORMAL" : type,
                environment == null || environment.isBlank() ? "NORMAL" : environment,
                seed,
                null,
                generateStructures);
    }
}

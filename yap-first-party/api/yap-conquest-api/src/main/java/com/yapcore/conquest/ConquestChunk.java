package com.yapcore.conquest;

import java.time.Instant;

/** One claimed chunk. */
public record ConquestChunk(
        String world,
        int chunkX,
        int chunkZ,
        long factionId,
        int powerCost,
        Instant claimedAt,
        boolean frozen) {
}

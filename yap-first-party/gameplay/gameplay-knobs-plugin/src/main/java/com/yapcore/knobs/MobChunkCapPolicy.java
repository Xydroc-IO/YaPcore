package com.yapcore.knobs;

/**
 * Pure per-mob chunk density check (Purpur-style max-per-chunk).
 * Limit {@code <= 0} means the cap is disabled.
 */
public final class MobChunkCapPolicy {

    private MobChunkCapPolicy() {
    }

    /**
     * @param alreadyInChunk count of the same entity type already in the chunk
     *                       (exclude the entity currently spawning)
     * @param maxPerChunk    configured cap; {@code <= 0} = off
     * @return true when the spawn should be cancelled
     */
    public static boolean overLimit(int alreadyInChunk, int maxPerChunk) {
        return maxPerChunk > 0 && alreadyInChunk >= maxPerChunk;
    }
}

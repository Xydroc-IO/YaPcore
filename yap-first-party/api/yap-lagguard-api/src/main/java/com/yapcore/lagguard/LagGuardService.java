package com.yapcore.lagguard;

import java.util.List;

/** Snapshot of lag-machine governor counters for ops / Prometheus. */
public interface LagGuardService {

    /** Hot chunk trip record for ops / dashboard. */
    record ChunkTrip(String world, int cx, int cz, long trips) {
    }

    long trips();

    long entitiesCancelled();

    long tntCancelled();

    long hopperThrottled();

    long redstoneThrottled();

    boolean enabled();

    /** Top chunks by cumulative trip count (highest first). */
    List<ChunkTrip> topChunks(int n);
}

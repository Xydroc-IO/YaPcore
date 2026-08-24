package com.yapcore.lagguard;

/** Snapshot of lag-machine governor counters for ops / Prometheus. */
public interface LagGuardService {

    long trips();

    long entitiesCancelled();

    long tntCancelled();

    long hopperThrottled();

    long redstoneThrottled();

    boolean enabled();
}

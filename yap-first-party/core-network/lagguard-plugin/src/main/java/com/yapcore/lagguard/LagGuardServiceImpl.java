package com.yapcore.lagguard;

public final class LagGuardServiceImpl implements LagGuardService {

    private final ChunkBudgetTracker tracker;
    private volatile LagGuardConfig config;

    public LagGuardServiceImpl(ChunkBudgetTracker tracker, LagGuardConfig config) {
        this.tracker = tracker;
        this.config = config;
    }

    public void setConfig(LagGuardConfig config) {
        this.config = config;
    }

    @Override
    public long trips() {
        return tracker.trips();
    }

    @Override
    public long entitiesCancelled() {
        return tracker.entitiesCancelled();
    }

    @Override
    public long tntCancelled() {
        return tracker.tntCancelled();
    }

    @Override
    public long hopperThrottled() {
        return tracker.hopperThrottled();
    }

    @Override
    public long redstoneThrottled() {
        return tracker.redstoneThrottled();
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }
}

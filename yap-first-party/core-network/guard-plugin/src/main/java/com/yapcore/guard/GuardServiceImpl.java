package com.yapcore.guard;

import java.util.UUID;

public final class GuardServiceImpl implements GuardService {

    private final ViolationTracker tracker;

    public GuardServiceImpl(ViolationTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public int violationCount(UUID playerUuid) {
        return tracker.count(playerUuid);
    }

    @Override
    public void resetViolations(UUID playerUuid) {
        tracker.reset(playerUuid);
    }
}

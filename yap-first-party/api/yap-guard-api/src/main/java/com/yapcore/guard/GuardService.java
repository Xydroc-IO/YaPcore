package com.yapcore.guard;

import java.util.UUID;

/**
 * Lightweight anti-cheat violation contract.
 * Provided by {@code YaPGuard} via {@code ServicesManager}.
 */
public interface GuardService {

    int violationCount(UUID playerUuid);

    void resetViolations(UUID playerUuid);
}

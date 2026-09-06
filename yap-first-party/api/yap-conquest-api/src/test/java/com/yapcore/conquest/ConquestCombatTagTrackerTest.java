package com.yapcore.conquest;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConquestCombatTagTrackerTest {

    @Test
    void tagsAndExpires() {
        var tracker = new ConquestCombatTagTracker();
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        tracker.tag(id, now, 10);
        assertTrue(tracker.isTagged(id, now.plusSeconds(5)));
        assertFalse(tracker.isTagged(id, now.plusSeconds(11)));
    }

    @Test
    void refreshExtends() {
        var tracker = new ConquestCombatTagTracker();
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        tracker.tag(id, now, 5);
        tracker.tag(id, now.plusSeconds(3), 10);
        assertTrue(tracker.isTagged(id, now.plusSeconds(12)));
        assertFalse(tracker.isTagged(id, now.plusSeconds(14)));
    }
}

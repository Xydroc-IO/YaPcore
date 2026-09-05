package com.yapcore.guard;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViolationTrackerTest {

    @Test
    void countResetAndIsolation() {
        ViolationTracker tracker = new ViolationTracker(new GuardConfig(null));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertEquals(0, tracker.count(a));
        tracker.state(a).violations = 4;
        assertEquals(4, tracker.count(a));
        assertEquals(0, tracker.count(b));

        tracker.reset(a);
        assertEquals(0, tracker.count(a));

        tracker.state(b).violations = 2;
        tracker.clearOffline(b);
        assertEquals(0, tracker.count(b));
    }
}

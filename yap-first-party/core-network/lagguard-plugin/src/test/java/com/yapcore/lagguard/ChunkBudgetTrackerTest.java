package com.yapcore.lagguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkBudgetTrackerTest {

    @Test
    void hopperWindowAllowsUntilLimitThenThrottle() {
        ChunkBudgetTracker tracker = new ChunkBudgetTracker();
        String key = ChunkBudgetTracker.key("world", 0, 0);
        assertTrue(tracker.tryHopper(key, 2, 20, 0));
        assertTrue(tracker.tryHopper(key, 2, 20, 1));
        assertFalse(tracker.tryHopper(key, 2, 20, 2));
        assertTrue(tracker.hopperThrottled() >= 1);
    }

    @Test
    void newWindowResetsCount() {
        ChunkBudgetTracker tracker = new ChunkBudgetTracker();
        String key = ChunkBudgetTracker.key("world", 1, 1);
        assertTrue(tracker.tryRedstone(key, 1, 20, 0));
        assertFalse(tracker.tryRedstone(key, 1, 20, 5));
        assertTrue(tracker.tryRedstone(key, 1, 20, 20));
    }

    @Test
    void topChunksOrdersByTrips() {
        ChunkBudgetTracker tracker = new ChunkBudgetTracker();
        tracker.tripEntity(ChunkBudgetTracker.key("world", 0, 0));
        tracker.tripHopper(ChunkBudgetTracker.key("lab", 2, 3));
        tracker.tripHopper(ChunkBudgetTracker.key("lab", 2, 3));
        tracker.tripTnt(ChunkBudgetTracker.key("lab", 2, 3));

        var top = tracker.topChunks(5);
        assertEquals(2, top.size());
        assertEquals("lab", top.get(0).world());
        assertEquals(2, top.get(0).cx());
        assertEquals(3, top.get(0).cz());
        assertEquals(3, top.get(0).trips());
        assertEquals(1, top.get(1).trips());
    }
}

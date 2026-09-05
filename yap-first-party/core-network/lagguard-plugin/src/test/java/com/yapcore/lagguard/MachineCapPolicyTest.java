package com.yapcore.lagguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineCapPolicyTest {

    @Test
    void pistonWindowCancelsAtLimit() {
        assertFalse(MachineCapPolicy.overWindowLimit(0, 64));
        assertFalse(MachineCapPolicy.overWindowLimit(63, 64));
        assertTrue(MachineCapPolicy.overWindowLimit(64, 64));
        assertFalse(MachineCapPolicy.overWindowLimit(100, 0));
    }

    @Test
    void minecartCap() {
        assertFalse(MachineCapPolicy.overMinecartCap(5, 16));
        assertTrue(MachineCapPolicy.overMinecartCap(16, 16));
        assertFalse(MachineCapPolicy.overMinecartCap(100, 0));
    }

    @Test
    void trackerPistonWindowAllowsLimitThenBlocks() {
        ChunkBudgetTracker tracker = new ChunkBudgetTracker();
        String key = ChunkBudgetTracker.key("world", 0, 0);
        for (int i = 0; i < 3; i++) {
            assertTrue(tracker.tryPiston(key, 3, 20, 0));
        }
        assertFalse(tracker.tryPiston(key, 3, 20, 0));
        assertTrue(tracker.tryPiston(key, 3, 20, 20)); // new window
    }
}

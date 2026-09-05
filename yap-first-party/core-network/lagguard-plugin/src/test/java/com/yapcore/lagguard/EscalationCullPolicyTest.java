package com.yapcore.lagguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscalationCullPolicyTest {

    @Test
    void disabledNeverCulls() {
        assertFalse(EscalationCullPolicy.shouldCull(false, 100, 50));
        assertFalse(EscalationCullPolicy.shouldCull(true, 10, 50));
        assertFalse(EscalationCullPolicy.shouldCull(true, 50, 0));
        assertTrue(EscalationCullPolicy.shouldCull(true, 50, 50));
    }

    @Test
    void itemsToRemoveRespectsCapAndMax() {
        assertEquals(0, EscalationCullPolicy.itemsToRemove(10, 40, 32));
        assertEquals(10, EscalationCullPolicy.itemsToRemove(50, 40, 32));
        assertEquals(32, EscalationCullPolicy.itemsToRemove(100, 40, 32));
        assertEquals(0, EscalationCullPolicy.itemsToRemove(100, 0, 32));
        assertEquals(0, EscalationCullPolicy.itemsToRemove(100, 40, 0));
    }
}

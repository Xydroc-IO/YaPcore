package com.yapcore.sched.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SchedCompatContextTest {

    @AfterEach
    void clear() {
        SchedCompatContext.clear();
    }

    @Test
    void scopedFromEventSetsAndRestoresEntity() throws Exception {
        Object player = new Object();
        Object nested = new Object();
        SchedCompatContext.setEntity(nested);
        try (AutoCloseable ignored = SchedCompatContext.scopedFromEvent(
                new EventAffinityTest.Playerish(player))) {
            assertSame(player, SchedCompatContext.currentEntity());
        }
        assertSame(nested, SchedCompatContext.currentEntity());
    }

    @Test
    void currentRegionProbeIsNullWithoutFolia() {
        assertNull(CurrentRegionProbe.probe());
    }
}

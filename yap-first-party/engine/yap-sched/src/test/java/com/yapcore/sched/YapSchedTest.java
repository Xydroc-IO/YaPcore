package com.yapcore.sched;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YapSchedTest {

    @Test
    void unitTestsAreNotFolia() {
        assertFalse(YapSched.isFolia());
    }

    @Test
    void deferredTaskIsCancellableBeforeBind() {
        YapSched.DeferredYapTask task = new YapSched.DeferredYapTask();
        assertFalse(task.isCancelled());
        task.cancel();
        assertTrue(task.isCancelled());
    }
}

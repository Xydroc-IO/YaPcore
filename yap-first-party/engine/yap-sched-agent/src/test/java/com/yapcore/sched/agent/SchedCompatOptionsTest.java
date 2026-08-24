package com.yapcore.sched.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedCompatOptionsTest {

    @Test
    void defaultsWarnAndMetricsOn() {
        SchedCompatOptions opts = SchedCompatOptions.defaults();
        assertTrue(opts.warnGlobal());
        assertTrue(opts.metrics());
    }

    @Test
    void parsesAgentArgs() {
        SchedCompatOptions opts = SchedCompatOptions.parse("warn=false,metrics=true");
        assertFalse(opts.warnGlobal());
        assertTrue(opts.metrics());
    }
}

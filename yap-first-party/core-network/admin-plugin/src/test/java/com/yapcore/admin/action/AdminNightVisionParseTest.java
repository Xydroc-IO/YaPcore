package com.yapcore.admin.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AdminNightVisionParseTest {

    @Test
    void parsesDurations() {
        assertEquals(AdminNightVision.Mode.MINUTES_15, AdminNightVision.parseMode("15m").orElseThrow());
        assertEquals(AdminNightVision.Mode.HOUR, AdminNightVision.parseMode("1h").orElseThrow());
        assertEquals(AdminNightVision.Mode.UNLIMITED, AdminNightVision.parseMode("on").orElseThrow());
        assertEquals(AdminNightVision.Mode.UNLIMITED, AdminNightVision.parseMode("unlimited").orElseThrow());
        assertEquals(AdminNightVision.Mode.OFF, AdminNightVision.parseMode("off").orElseThrow());
        assertTrue(AdminNightVision.parseMode("nope").isEmpty());
    }
}

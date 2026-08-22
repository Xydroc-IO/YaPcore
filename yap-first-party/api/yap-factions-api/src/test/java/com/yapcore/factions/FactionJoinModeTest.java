package com.yapcore.factions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionJoinModeTest {

    @Test
    void parsesModes() {
        assertEquals(FactionJoinMode.OPEN, FactionJoinMode.parse("open").orElseThrow());
        assertEquals(FactionJoinMode.INVITE, FactionJoinMode.parse("INVITE").orElseThrow());
        assertEquals(FactionJoinMode.CLOSED, FactionJoinMode.parse("closed").orElseThrow());
    }

    @Test
    void rejectsUnknown() {
        assertTrue(FactionJoinMode.parse("foobar").isEmpty());
    }
}

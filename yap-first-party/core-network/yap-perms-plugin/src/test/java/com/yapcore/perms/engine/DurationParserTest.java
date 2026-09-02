package com.yapcore.perms.engine;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationParserTest {

    @Test
    void parsesCompound() {
        Duration d = DurationParser.parse("1d12h").orElseThrow();
        assertEquals(Duration.ofHours(36), d);
    }

    @Test
    void parsesWeek() {
        assertEquals(Duration.ofDays(7), DurationParser.parse("1w").orElseThrow());
    }

    @Test
    void permanentIsEmpty() {
        assertTrue(DurationParser.parse("permanent").isEmpty());
        assertTrue(DurationParser.parse("0").isEmpty());
        assertFalse(DurationParser.looksLike("permanent"));
    }

    @Test
    void rejectsPlayerNames() {
        assertFalse(DurationParser.looksLike("Steve"));
        assertFalse(DurationParser.looksLike("world_nether"));
    }

    @Test
    void formatRoundTrip() {
        assertEquals("1d12h", DurationParser.format(Duration.ofHours(36)));
        assertEquals("ready", DurationParser.formatSeconds(0));
    }
}

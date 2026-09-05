package com.yapcore.moderation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationParserTest {

    @Test
    void parsesRelativeDurationsNearNow() {
        long now = System.currentTimeMillis();
        long m30 = DurationParser.parseToEpochMs("30m");
        assertTrue(Math.abs(m30 - (now + 30L * 60_000L)) < 2000L);
        long h2 = DurationParser.parseToEpochMs("2h");
        assertTrue(Math.abs(h2 - (now + 2L * 3_600_000L)) < 2000L);
        long compound = DurationParser.parseToEpochMs("7d1h");
        assertTrue(Math.abs(compound - (now + 7L * 86_400_000L + 3_600_000L)) < 2000L);
        long weeks = DurationParser.parseToEpochMs("2w");
        assertTrue(Math.abs(weeks - (now + 14L * 86_400_000L)) < 2000L);
    }

    @Test
    void permanentTokensAreZero() {
        assertEquals(0L, DurationParser.parseToEpochMs("perm"));
        assertEquals(0L, DurationParser.parseToEpochMs("permanent"));
        assertEquals(0L, DurationParser.parseToEpochMs("forever"));
    }

    @Test
    void rejectsBlankAndGarbage() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToEpochMs(""));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToEpochMs(null));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToEpochMs("nope"));
    }

    @Test
    void formatExpiryLabels() {
        assertEquals("Permanent", DurationParser.formatExpiry(0L));
        assertEquals("Expired", DurationParser.formatExpiry(System.currentTimeMillis() - 60_000L));
        String near = DurationParser.formatExpiry(System.currentTimeMillis() + 90_000L);
        assertTrue(near.contains("m") && near.contains("s"), near);
    }
}

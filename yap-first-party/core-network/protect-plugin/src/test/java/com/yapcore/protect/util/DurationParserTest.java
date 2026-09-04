package com.yapcore.protect.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationParserTest {

    @Test
    void parsesCompoundTokens() {
        assertEquals(TimeUnit.MINUTES.toMillis(30), DurationParser.parseToMillis("30m"));
        assertEquals(TimeUnit.HOURS.toMillis(2), DurationParser.parseToMillis("2h"));
        assertEquals(
                TimeUnit.DAYS.toMillis(7) + TimeUnit.HOURS.toMillis(1),
                DurationParser.parseToMillis("7d1h"));
        assertEquals(TimeUnit.DAYS.toMillis(14), DurationParser.parseToMillis("2w"));
    }

    @Test
    void bareDigitsMeanHours() {
        assertEquals(TimeUnit.HOURS.toMillis(12), DurationParser.parseToMillis("12"));
    }

    @Test
    void rejectsBlankAndGarbage() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToMillis(""));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToMillis("nope"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parseToMillis(null));
    }
}

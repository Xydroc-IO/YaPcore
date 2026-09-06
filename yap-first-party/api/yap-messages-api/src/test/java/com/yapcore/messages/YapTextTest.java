package com.yapcore.messages;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YapTextTest {

    @Test
    void applyBraceAndPercent() {
        assertEquals("Hello Steve", YapText.apply("Hello {player}", Map.of("player", "Steve")));
        assertEquals("Hello Steve", YapText.apply("Hello %player%", Map.of("player", "Steve")));
    }

    @Test
    void plainStripsCodes() {
        assertEquals("Hi", YapText.plain("&aHi"));
        assertEquals("Hi", YapText.plain("§cHi"));
    }

    @Test
    void toMapPairs() {
        Map<String, String> m = YapText.toMap("a", "1", "b", "2");
        assertEquals("1", m.get("a"));
        assertEquals("2", m.get("b"));
    }
}

class YapMessageBundleTest {

    @Test
    void standardDefaultsPresent() {
        YapMessageBundle b = YapMessageBundle.fromSection(null);
        assertTrue(b.has(YapMessageBundle.KEY_NO_PERMISSION));
        assertTrue(b.raw(YapMessageBundle.KEY_NO_PERMISSION).contains("{node}"));
        assertFalse(b.raw(YapMessageBundle.KEY_PLAYERS_ONLY).isBlank());
    }

    @Test
    void applyOnRawTemplate() {
        YapMessageBundle b = new YapMessageBundle(
                Map.of("muted", "&cMuted: {reason}"), false);
        assertEquals("&cMuted: spam", YapText.apply(b.raw("muted"), Map.of("reason", "spam")));
    }
}

package com.yapcore.holo.cmd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HologramCommandParseTest {

    @Test
    void jsonRoundTripShape() {
        String json = HologramCommandParse.toJson(List.of(
                new HologramCommandParse.Row("spawn", "world", 0.5, 66.0, -2.0, 48.0,
                        "&6Welcome|&7YaPcore", "", "", "", 1)));
        assertTrue(json.startsWith("[{"));
        assertTrue(json.contains("\"id\":\"spawn\""));
        assertTrue(json.contains("\"lines\":\"&6Welcome|&7YaPcore\""));
        assertTrue(json.contains("\"view\":48.0"));
        assertTrue(json.contains("\"attach\":\"\""));
    }

    @Test
    void splitLinesKeepsPipes() {
        assertEquals(List.of("&6A", "&7B"), HologramCommandParse.splitLines("&6A|&7B"));
        assertEquals(List.of("&f"), HologramCommandParse.splitLines("  "));
        assertTrue(HologramCommandParse.validId("spawn_1"));
        assertFalse(HologramCommandParse.validId("bad id"));
    }

    @Test
    void indexOfAt() {
        String[] args = {"create", "spawn", "at", "world", "1", "2", "3"};
        assertEquals(2, HologramCommandParse.indexOf(args, "at", 2));
        assertEquals("1 2 3", HologramCommandParse.join(args, 4));
    }
}

package com.yapcore.packs;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacksUnitTest {

    @Test
    void activeJsonParsePacksAndFlags() {
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String json = """
                {
                  "enabled": true,
                  "forced": true,
                  "prompt": "Please accept",
                  "packs": [
                    {"file": "a.zip", "url": "https://cdn.example/a.zip", "sha1": "ABCD", "uuid": "%s"}
                  ]
                }
                """.formatted(id);
        PacksPlugin.Manifest m = PacksPlugin.Manifest.parse(json);
        assertTrue(m.enabled());
        assertTrue(m.forced());
        assertEquals("Please accept", m.prompt());
        assertEquals(1, m.packs().size());
        PacksPlugin.PackEntry pack = m.packs().getFirst();
        assertEquals("a.zip", pack.file());
        assertEquals("https://cdn.example/a.zip", pack.url());
        assertEquals("abcd", pack.sha1());
        assertEquals(id, pack.uuid());
    }

    @Test
    void emptyManifestAndDisabledForcedFlags() {
        PacksPlugin.Manifest empty = PacksPlugin.Manifest.empty();
        assertTrue(empty.enabled());
        assertFalse(empty.forced());
        assertTrue(empty.packs().isEmpty());

        PacksPlugin.Manifest off = PacksPlugin.Manifest.parse(
                "{\"enabled\": false, \"forced\":false, \"prompt\": \"\", \"packs\": []}");
        assertFalse(off.enabled());
        assertFalse(off.forced());
    }

    @Test
    void resolvePromptAndSha1HexRoundTrip() throws Exception {
        assertTrue(PacksPlugin.resolvePrompt(null).contains("additional resource packs"));
        assertTrue(PacksPlugin.resolvePrompt("").contains("additional resource packs"));
        assertTrue(PacksPlugin.resolvePrompt("{\"text\":\"x\"}").contains("Click Yes"));
        assertEquals("Custom", PacksPlugin.resolvePrompt("Custom"));

        byte[] hash = HexFormat.of().parseHex("deadbeefcafebabe0123456789abcdef01234567");
        assertEquals(20, hash.length);
        assertEquals("deadbeefcafebabe0123456789abcdef01234567", HexFormat.of().formatHex(hash));

        try (InputStream in = PacksPlugin.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in);
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("main: com.yapcore.packs.PacksPlugin"));
            assertTrue(yml.contains("yappacks"));
        }
    }
}

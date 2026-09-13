package com.yapcore.link.bedrock.raknet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RakNetUnconnectedMotdTest {

    @Test
    void buildMotdHasTrailingPortsAndStripsColorCodes() {
        String motd = RakNetUnconnected.buildMotd(
                "&l&4YaPcore Test;Server",
                2169,
                "26.45",
                1,
                100,
                12345L,
                "Sub",
                19132,
                19132);
        assertTrue(motd.startsWith("MCPE;"));
        assertFalse(motd.contains("&l"));
        assertFalse(motd.contains("&4"));
        assertFalse(motd.contains("Test;Server")); // semicolon stripped from name
        String[] parts = motd.split(";", -1);
        assertTrue(parts.length >= 12, "fields=" + parts.length + " motd=" + motd);
        assertEquals("19132", parts[parts.length - 3]);
        assertEquals("19132", parts[parts.length - 2]);
    }

    @Test
    void sanitizeMotdPartFallback() {
        assertEquals("YaP Link", RakNetUnconnected.sanitizeMotdPart(null, "YaP Link"));
        assertEquals("YaP Link", RakNetUnconnected.sanitizeMotdPart("   ", "YaP Link"));
        assertEquals("Hello", RakNetUnconnected.sanitizeMotdPart("&aHello", "YaP Link"));
    }
}

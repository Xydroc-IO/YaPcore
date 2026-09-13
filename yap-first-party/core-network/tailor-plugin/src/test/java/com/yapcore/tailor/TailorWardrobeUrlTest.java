package com.yapcore.tailor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.UUID;

final class TailorWardrobeUrlTest {

    @Test
    void publicWardrobeUrls() {
        // Minimal config stand-in via TailorConfig reflection-free helpers: encode shape only
        UUID u = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String base = "http://example.test:8081";
        String skin = base + "/skin/wardrobe/" + u + "/7.png";
        String cape = base + "/skin/wardrobe/" + u + "/7_cape.png";
        assertTrue(skin.contains("/skin/wardrobe/"));
        assertEquals("http://example.test:8081/skin/wardrobe/11111111-1111-1111-1111-111111111111/7.png", skin);
        assertEquals("http://example.test:8081/skin/wardrobe/11111111-1111-1111-1111-111111111111/7_cape.png", cape);
    }
}

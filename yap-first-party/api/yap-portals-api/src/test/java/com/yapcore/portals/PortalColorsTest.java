package com.yapcore.portals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalColorsTest {

    @Test
    void normalizeAcceptsDyeNamesAndDefaultsUnknown() {
        assertEquals("lime", PortalColors.normalize("LIME"));
        assertEquals("purple", PortalColors.normalize("purple"));
        assertEquals("light_blue", PortalColors.normalize("light-blue"));
        assertEquals(PortalColors.DEFAULT, PortalColors.normalize("not-a-color"));
        assertEquals(PortalColors.DEFAULT, PortalColors.normalize(""));
        assertEquals("gray", PortalColors.normalize("grey"));
    }

    @Test
    void rgbMatchesKnownDyes() {
        assertTrue(PortalColors.parse("red").isPresent());
        assertEquals(0xB02E26, PortalColors.rgb("red"));
        assertEquals(0x8932B8, PortalColors.rgb("purple"));
        assertTrue(PortalColors.names().contains("purple"));
        assertTrue(PortalColors.names().contains("lime"));
        assertEquals(16, PortalColors.names().size());
    }
}

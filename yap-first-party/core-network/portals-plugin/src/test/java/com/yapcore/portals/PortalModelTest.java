package com.yapcore.portals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalModelTest {

    @Test
    void withersPreserveIdentityAndNormalizeName() {
        PortalCuboid box = PortalCuboid.of(0, 64, 0, 2, 66, 2);
        Portal p = new Portal("To-Survival", "world", box, "survival", "", 3, true, "");
        assertEquals("to-survival", p.name());
        Portal next = p.withTarget("lobby").withCooldown(5).withEnabled(false).withPermission("vip.portal")
                .withColor("lime");
        assertEquals("to-survival", next.name());
        assertEquals("lobby", next.targetServer());
        assertEquals(5, next.cooldownSeconds());
        assertFalseEnabled(next);
        assertEquals("vip.portal", next.permission());
        assertEquals("lime", next.color());
        assertEquals("purple", p.color());
        assertTrue(next.cuboid().containsBlock(1, 65, 1));
    }

    private static void assertFalseEnabled(Portal next) {
        org.junit.jupiter.api.Assertions.assertFalse(next.enabled());
    }
}

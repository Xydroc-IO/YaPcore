package com.yapcore.dungeons.portal;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalStructureSizingTest {

    @Test
    void defaultsMatchNetherStyleFrame() {
        PortalStructure s = new PortalStructure(Material.OBSIDIAN, Material.NETHER_PORTAL, 4, 5);
        assertEquals(4, s.outerWidth());
        assertEquals(5, s.outerHeight());
        assertEquals(Material.OBSIDIAN, s.frameMaterial());
    }

    @Test
    void clampsMinimumSize() {
        PortalStructure s = new PortalStructure(Material.OBSIDIAN, Material.NETHER_PORTAL, 1, 1);
        assertTrue(s.outerWidth() >= 3);
        assertTrue(s.outerHeight() >= 4);
    }
}

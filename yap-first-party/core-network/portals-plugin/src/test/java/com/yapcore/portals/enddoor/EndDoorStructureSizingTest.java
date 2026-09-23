package com.yapcore.portals.enddoor;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndDoorStructureSizingTest {

    @Test
    void defaultsMatchNetherStyleFrame() {
        EndDoorStructure s = new EndDoorStructure(Material.OBSIDIAN, Material.NETHER_PORTAL, 4, 5);
        assertEquals(4, s.outerWidth());
        assertEquals(5, s.outerHeight());
        assertEquals(Material.OBSIDIAN, s.frameMaterial());
    }

    @Test
    void clampsMinimumSize() {
        EndDoorStructure s = new EndDoorStructure(Material.OBSIDIAN, Material.NETHER_PORTAL, 1, 1);
        assertTrue(s.outerWidth() >= 3);
        assertTrue(s.outerHeight() >= 4);
    }
}

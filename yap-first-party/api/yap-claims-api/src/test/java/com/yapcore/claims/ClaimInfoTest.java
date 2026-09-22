package com.yapcore.claims;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimInfoTest {

    @Test
    void containsAndSubdivision() {
        UUID owner = UUID.randomUUID();
        ClaimInfo top = new ClaimInfo(1, owner, "lobby", "world", 0, 15, 0, 15, "home", null);
        assertFalse(top.isSubdivision());
        assertTrue(top.contains("world", 8, 8));
        assertFalse(top.contains("world", 16, 8));
        assertFalse(top.contains("nether", 8, 8));

        ClaimInfo sub = new ClaimInfo(2, owner, "lobby", "world", 0, 7, 0, 7, "plot", 1L);
        assertTrue(sub.isSubdivision());
        assertEquals(1L, sub.parentId());
    }
}

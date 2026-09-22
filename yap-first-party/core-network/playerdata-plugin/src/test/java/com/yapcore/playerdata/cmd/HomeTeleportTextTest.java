package com.yapcore.playerdata.cmd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeTeleportTextTest {

    @Test
    void defaultHomeIsNotHomeHome() {
        assertEquals("§aTeleported home.", HomeTeleportText.arrived("home"));
        assertEquals("§aTeleported home.", HomeTeleportText.arrived("Home"));
        assertEquals("§aTeleported home.", HomeTeleportText.arrived("  HOME  "));
        assertEquals("§aTeleported home.", HomeTeleportText.arrived(""));
        assertEquals("§aTeleported home.", HomeTeleportText.arrived(null));
    }

    @Test
    void namedHomeKeepsTheName() {
        assertEquals("§aTeleported to home §fmine", HomeTeleportText.arrived("Mine"));
        assertEquals("§aTeleported to home §fshop", HomeTeleportText.arrived("shop"));
    }
}

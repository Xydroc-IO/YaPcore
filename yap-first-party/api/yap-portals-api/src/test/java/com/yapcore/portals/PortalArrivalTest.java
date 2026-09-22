package com.yapcore.portals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalArrivalTest {

    @Test
    void parseRecognizesWildAliases() {
        assertEquals(PortalArrival.RTP, PortalArrival.parse("rtp"));
        assertEquals(PortalArrival.RTP, PortalArrival.parse("wild"));
        assertEquals(PortalArrival.RTP, PortalArrival.parse("RANDOM"));
        assertEquals(PortalArrival.SPAWN, PortalArrival.parse("spawn"));
        assertEquals(PortalArrival.SPAWN, PortalArrival.parse(null));
        assertTrue(PortalArrival.known("rtp"));
        assertTrue(PortalArrival.known("spawn"));
    }

    @Test
    void parseRecognizesHome() {
        assertEquals(PortalArrival.HOME, PortalArrival.parse("home"));
        assertEquals(PortalArrival.HOME, PortalArrival.parse("sethome"));
        assertEquals(PortalArrival.HOME, PortalArrival.parse("home:cabin"));
        assertEquals("cabin", PortalArrival.homeNameOf("home:cabin"));
        assertEquals("home", PortalArrival.homeNameOf("home"));
        assertTrue(PortalArrival.known("home"));
        assertTrue(PortalArrival.known("home:base"));
        assertFalse(PortalArrival.known("nether"));
    }
}

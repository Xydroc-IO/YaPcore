package com.yapcore.lib.packet;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PacketEventCancelTest {

    @Test
    void monitorCannotCancel() {
        PacketEvent event = new PacketEvent(null, null, PacketDirection.SERVERBOUND,
                PacketContainer.wrap("x", PacketTypes.Play.Client.CHAT), true);
        event.withPriority(PacketPriority.MONITOR).setCancelled(true);
        assertFalse(event.cancelled());
        event.withPriority(PacketPriority.HIGHEST).setCancelled(true);
        assertTrue(event.cancelled());
    }

    @Test
    void regionDecisionCanCancel() {
        PacketEvent event = new PacketEvent(null, null, PacketDirection.SERVERBOUND,
                PacketContainer.wrap("x", PacketTypes.Play.Client.CHAT), false);
        event.withPriority(PacketPriority.NORMAL).setCancelled(true);
        assertTrue(event.cancelled());
    }

    @Test
    void handshakeAddressSurvivesObserveCopy() {
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 25565);
        PacketEvent event = new PacketEvent(null, null, PacketDirection.SERVERBOUND,
                PacketContainer.wrap("x", PacketTypes.Handshake.Client.INTENTION), true, addr);
        assertEquals(addr, event.address());
        assertEquals(addr, event.asObserveOnly().address());
    }

    @Test
    void observeOnlyIgnoresRewrite() {
        PacketContainer original = PacketContainer.wrap("a", PacketTypes.Play.Client.CHAT);
        PacketEvent event = new PacketEvent(null, null, PacketDirection.SERVERBOUND, original, true);
        PacketEvent observe = event.asObserveOnly();
        observe.setCancelled(true);
        observe.setHandle("b");
        assertFalse(observe.cancelled());
        assertEqualsHandle("a", observe);
    }

    private static void assertEqualsHandle(String expected, PacketEvent event) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, event.packet().handle());
    }
}

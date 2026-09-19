package com.yapcore.lib.inject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PacketHoldGateTest {

    @Test
    void queuesWhileHeldThenDrainsInOrder() {
        PacketHoldGate<String> gate = new PacketHoldGate<>();
        assertTrue(gate.acceptNow());
        gate.hold();
        assertFalse(gate.acceptNow());
        gate.enqueue("a");
        gate.enqueue("b");
        assertNull(gate.poll());
        gate.release();
        assertEquals("a", gate.poll());
        assertEquals("b", gate.poll());
        assertNull(gate.poll());
        assertTrue(gate.acceptNow());
    }

    @Test
    void enqueueWhenBusyPreservesFirstInLine() {
        PacketHoldGate<Integer> gate = new PacketHoldGate<>();
        gate.enqueue(1);
        assertFalse(gate.acceptNow());
        gate.enqueue(2);
        assertEquals(1, gate.poll());
        assertEquals(2, gate.poll());
    }
}

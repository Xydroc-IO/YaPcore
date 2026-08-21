package com.yapcore.protocol.via;

import com.yapcore.protocol.java.ProtocolBand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViaSessionForwardTest {

    @Test
    void futureClientNeedsForwardRemap() {
        ViaSession session = new ViaSession(800, 776);
        assertEquals(ProtocolBand.V_FUTURE, session.clientBand());
        assertEquals(ProtocolBand.V26_2, session.serverBand());
        assertTrue(session.needsRemap());
        assertTrue(session.needsForward());
        assertFalse(session.needsBackwards());
    }

    @Test
    void sameProtocolNoRemap() {
        ViaSession session = new ViaSession(776, 776);
        assertFalse(session.needsRemap());
        assertFalse(session.needsForward());
    }

    @Test
    void olderClientNeedsBackwards() {
        ViaSession session = new ViaSession(47, 776);
        assertTrue(session.needsRemap());
        assertTrue(session.needsBackwards());
        assertFalse(session.needsForward());
    }

    @Test
    void v26_2IsCapped() {
        assertEquals(776, ProtocolBand.V26_2.maxProtocol());
        assertEquals(ProtocolBand.V_FUTURE, ProtocolBand.of(777));
        assertEquals(ProtocolBand.V26_2, ProtocolBand.of(776));
    }
}

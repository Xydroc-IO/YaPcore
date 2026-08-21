package com.yapcore.crossplay.bedrock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4.5 — continuous Paper column stream bookkeeping. */
class BedrockColumnStreamerTest {

    @Test
    void initialRingMarksSentAndSecondCallIsEmpty() {
        BedrockColumnStreamer s = new BedrockColumnStreamer();
        long guid = 1L;
        s.setRadius(guid, 8);
        List<BedrockColumnStreamer.Column> first = s.initialRing(guid, 8, -8, 2);
        // 5×5 = 25 columns for ring=2
        assertEquals(25, first.size());
        assertTrue(s.wasSent(guid, 0, -1));
        assertEquals(0, s.initialRing(guid, 8, -8, 2).size());
    }

    @Test
    void missingAroundOnlyFiresOnChunkCross() {
        BedrockColumnStreamer s = new BedrockColumnStreamer();
        long guid = 42L;
        s.setRadius(guid, 3);
        s.initialRing(guid, 0, 0, 1); // marks 3×3 around 0,0
        assertTrue(s.missingAround(guid, 5, 5, false).isEmpty()); // same chunk
        List<BedrockColumnStreamer.Column> next = s.missingAround(guid, 48, 0, false); // cx=3
        assertFalse(next.isEmpty());
        // Same chunk again → empty
        assertTrue(s.missingAround(guid, 50, 2, false).isEmpty());
    }

    @Test
    void invalidateAllowsResend() {
        BedrockColumnStreamer s = new BedrockColumnStreamer();
        long guid = 7L;
        s.initialRing(guid, 0, 0, 1);
        assertTrue(s.wasSent(guid, 0, 0));
        s.invalidate(guid, 0, 0);
        assertFalse(s.wasSent(guid, 0, 0));
        s.invalidateAllSessions(0, 0);
    }

    @Test
    void clearRemovesSession() {
        BedrockColumnStreamer s = new BedrockColumnStreamer();
        s.setRadius(9L, 4);
        s.initialRing(9L, 16, 16, 1);
        s.clear(9L);
        assertFalse(s.wasSent(9L, 1, 1));
        assertEquals(8, s.radius(9L)); // default after clear
    }
}

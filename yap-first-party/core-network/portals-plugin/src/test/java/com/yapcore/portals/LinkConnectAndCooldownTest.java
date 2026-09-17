package com.yapcore.portals;

import com.yapcore.portals.service.LinkConnect;
import com.yapcore.portals.service.PortalCooldown;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LinkConnectAndCooldownTest {

    @Test
    void connectPayloadRoundTrip() throws Exception {
        byte[] bytes = LinkConnect.connectPayload("survival");
        assertEquals("survival", LinkConnect.decodeConnectTarget(bytes));
    }

    @Test
    void cooldownMarksAndExpires() {
        PortalCooldown cd = new PortalCooldown();
        UUID id = UUID.randomUUID();
        long t0 = 1_000_000L;
        assertTrue(cd.ready(id, t0));
        cd.mark(id, 3, t0);
        assertFalse(cd.ready(id, t0 + 500));
        assertEquals(3, cd.remainingSeconds(id, t0));
        assertEquals(1, cd.remainingSeconds(id, t0 + 2_500));
        assertTrue(cd.ready(id, t0 + 3_000));
        assertEquals(0, cd.remainingSeconds(id, t0 + 3_000));
    }
}

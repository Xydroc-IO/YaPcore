package com.yapcore.link.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MojangAuthTest {

    @Test
    void retriesTransientMojang204AndRateLimits() {
        assertTrue(MojangAuth.retryableStatus(204));
        assertTrue(MojangAuth.retryableStatus(408));
        assertTrue(MojangAuth.retryableStatus(429));
        assertTrue(MojangAuth.retryableStatus(503));
        assertFalse(MojangAuth.retryableStatus(200));
        assertFalse(MojangAuth.retryableStatus(403));
    }

    @Test
    void parseProfileDashesMojangUuid() {
        MojangAuth.Profile p = MojangAuth.parseProfile(
                "{\"id\":\"11111111222233334444555555555555\",\"name\":\"Steve\"}", "x");
        assertEquals(UUID.fromString("11111111-2222-3333-4444-555555555555"), p.id());
        assertEquals("Steve", p.name());
        assertTrue(p.properties().isEmpty());
    }
}

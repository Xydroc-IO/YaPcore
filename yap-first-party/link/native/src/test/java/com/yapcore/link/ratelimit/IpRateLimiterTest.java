package com.yapcore.link.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpRateLimiterTest {

    @Test
    void allowsUnderLimitThenBlocks() {
        IpRateLimiter lim = new IpRateLimiter();
        String ip = "203.0.113.10";
        assertTrue(lim.tryAcquire(ip, 3, 60_000L));
        assertTrue(lim.tryAcquire(ip, 3, 60_000L));
        assertTrue(lim.tryAcquire(ip, 3, 60_000L));
        assertFalse(lim.tryAcquire(ip, 3, 60_000L));
    }

    @Test
    void isolatesIps() {
        IpRateLimiter lim = new IpRateLimiter();
        assertTrue(lim.tryAcquire("203.0.113.1", 1, 60_000L));
        assertFalse(lim.tryAcquire("203.0.113.1", 1, 60_000L));
        assertTrue(lim.tryAcquire("203.0.113.2", 1, 60_000L));
    }
}

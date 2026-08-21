package com.yaplabs.yapengine.sync.lease;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fast semantic checks for {@link AtomicLeaseManager}. */
class AtomicLeaseManagerTest {

    @Test
    void singleOwnerAcquireRelease() {
        AtomicLeaseManager mgr = new AtomicLeaseManager();
        AtomicLeaseManager.Lease lease = mgr.tryAcquire("inv:player1", "t7");
        assertNotNull(lease);
        assertTrue(mgr.isHeld("inv:player1"));
        assertTrue(mgr.release(lease));
        assertFalse(mgr.isHeld("inv:player1"));
    }

    @Test
    void secondOwnerDeniedWhileHeld() {
        AtomicLeaseManager mgr = new AtomicLeaseManager();
        AtomicLeaseManager.Lease first = mgr.tryAcquire("chunk:0,0", "t8");
        assertNotNull(first);
        assertNull(mgr.tryAcquire("chunk:0,0", "spatial-nw"));
        assertTrue(mgr.release(first));
        assertNotNull(mgr.tryAcquire("chunk:0,0", "spatial-nw"));
    }

    @Test
    void sameOwnerIsReentrant() {
        AtomicLeaseManager mgr = new AtomicLeaseManager();
        AtomicLeaseManager.Lease a = mgr.tryAcquire("item:sword", "t8");
        AtomicLeaseManager.Lease b = mgr.tryAcquire("item:sword", "t8");
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(a.leaseId(), b.leaseId());
    }
}

package com.yaplabs.yapengine.sync.lease;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.pastalab.fray.junit.junit5.FrayTestExtension;
import org.pastalab.fray.junit.junit5.annotations.FrayTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic concurrency tests for {@link AtomicLeaseManager}.
 * Fray forces alternate interleavings across contended acquire/release.
 * Uses a near-infinite lease TTL so wall-clock pauses under Fray do not
 * spuriously expire leases mid-critical-section.
 */
@ExtendWith(FrayTestExtension.class)
class AtomicLeaseManagerFrayTest {

    @FrayTest(iterations = 500)
    @Timeout(120)
    void mutualExclusionUnderContention() throws InterruptedException {
        AtomicLeaseManager mgr = new AtomicLeaseManager(TimeUnit.DAYS.toNanos(1));
        AtomicInteger holders = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        AtomicInteger acquires = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(8);

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String owner = "worker-" + i;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    for (int n = 0; n < 50; n++) {
                        AtomicLeaseManager.Lease lease = mgr.tryAcquire("shared-resource", owner);
                        if (lease == null) {
                            continue;
                        }
                        acquires.incrementAndGet();
                        int now = holders.incrementAndGet();
                        maxConcurrent.accumulateAndGet(now, Math::max);
                        holders.decrementAndGet();
                        mgr.release(lease);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, owner);
            threads.add(t);
            t.start();
        }

        start.countDown();
        done.await();
        for (Thread t : threads) {
            t.join();
        }
        assertTrue(acquires.get() > 0, "at least one acquire should succeed");
        assertEquals(1, maxConcurrent.get(), "at most one holder may own the lease");
        assertFalse(mgr.isHeld("shared-resource"));
    }
}

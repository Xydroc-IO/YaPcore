package com.yaplabs.yapengine.sync.lease;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

/**
 * JCStress: two actors racing tryAcquire on the same key and holding until arbiter.
 * Acceptable: exactly one grant. Forbidden: both grant (true mutual exclusion break).
 */
@JCStressTest
@Outcome(id = "1, 0", expect = Expect.ACCEPTABLE, desc = "actor1 won the lease")
@Outcome(id = "0, 1", expect = Expect.ACCEPTABLE, desc = "actor2 won the lease")
@Outcome(id = "1, 1", expect = Expect.FORBIDDEN, desc = "both actors held the lease")
@Outcome(id = "0, 0", expect = Expect.FORBIDDEN, desc = "neither actor acquired")
@State
public class AtomicLeaseMutualExclusionStress {

    private final AtomicLeaseManager manager = new AtomicLeaseManager();
    private volatile AtomicLeaseManager.Lease lease1;
    private volatile AtomicLeaseManager.Lease lease2;

    @Actor
    public void actor1(II_Result r) {
        AtomicLeaseManager.Lease lease = manager.tryAcquire("stress-key", "actor1");
        lease1 = lease;
        r.r1 = lease != null ? 1 : 0;
    }

    @Actor
    public void actor2(II_Result r) {
        AtomicLeaseManager.Lease lease = manager.tryAcquire("stress-key", "actor2");
        lease2 = lease;
        r.r2 = lease != null ? 1 : 0;
    }

    @Arbiter
    public void cleanup() {
        if (lease1 != null) {
            manager.release(lease1);
        }
        if (lease2 != null) {
            manager.release(lease2);
        }
    }
}

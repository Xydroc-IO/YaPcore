package com.yaplabs.yapengine.sync.lease;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

/**
 * Visibility: after acquire+release completes, a later acquire must succeed.
 */
@JCStressTest
@Outcome(id = "1", expect = Expect.ACCEPTABLE, desc = "lease free after release")
@Outcome(id = "0", expect = Expect.FORBIDDEN, desc = "lease stuck held after release")
@State
public class AtomicLeaseReleaseVisibilityStress {

    private final AtomicLeaseManager manager = new AtomicLeaseManager();

    @Actor
    public void writer() {
        AtomicLeaseManager.Lease lease = manager.tryAcquire("vis-key", "writer");
        if (lease != null) {
            manager.release(lease);
        }
    }

    @Arbiter
    public void afterBothActors(I_Result r) {
        AtomicLeaseManager.Lease lease = manager.tryAcquire("vis-key", "arbiter");
        r.r1 = lease != null ? 1 : 0;
        if (lease != null) {
            manager.release(lease);
        }
    }
}

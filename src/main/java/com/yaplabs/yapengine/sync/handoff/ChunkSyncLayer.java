package com.yaplabs.yapengine.sync.handoff;

import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.sequencing.SequenceToken;
import com.yaplabs.yapengine.sync.boundary.BoundaryArbitrator;
import com.yaplabs.yapengine.sync.dlm.ChunkSyncDlm;
import com.yaplabs.yapengine.sync.lease.AtomicLeaseManager;

import java.util.Objects;

/**
 * Facade for Threads 7–8 (v1.1): DLM lease pipeline + boundary arbitration.
 */
public final class ChunkSyncLayer {

    public record Handoff(
            String entityId,
            String inventoryKey,
            SpatialQuadrant from,
            SpatialQuadrant to,
            SequenceToken token,
            Runnable applyOnDestination
    ) {
        public Handoff {
            Objects.requireNonNull(entityId);
            Objects.requireNonNull(inventoryKey);
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
            Objects.requireNonNull(token);
            Objects.requireNonNull(applyOnDestination);
        }
    }

    private final ChunkSyncDlm dlm = new ChunkSyncDlm();
    private final BoundaryArbitrator boundary = new BoundaryArbitrator(dlm);

    public AtomicLeaseManager leases() {
        return dlm.leases();
    }

    public ChunkSyncDlm dlm() {
        return dlm;
    }

    public BoundaryArbitrator boundary() {
        return boundary;
    }

    public void start() {
        dlm.start();
        boundary.start();
    }

    public void stop() {
        boundary.stop();
        dlm.stop();
    }

    public void submitHandoff(Handoff handoff) {
        Objects.requireNonNull(handoff);
        boundary.submit(new BoundaryArbitrator.BoundaryTransaction(
                handoff.entityId(),
                handoff.inventoryKey(),
                handoff.from(),
                handoff.to(),
                handoff.token(),
                handoff.applyOnDestination()
        ));
    }

    /**
     * Soft backpressure for long soaks — returns false when the T7/T8 pipeline
     * is already deeper than {@code maxPending} so producers can yield.
     */
    public boolean trySubmitHandoff(Handoff handoff, int maxPending) {
        if (pending() >= maxPending) {
            return false;
        }
        submitHandoff(handoff);
        return true;
    }

    public long getProcessed() {
        return boundary.getProcessed();
    }

    public int pending() {
        return boundary.pending() + dlm.pending();
    }
}

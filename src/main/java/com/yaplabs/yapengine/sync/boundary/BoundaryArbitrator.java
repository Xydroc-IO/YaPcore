package com.yaplabs.yapengine.sync.boundary;

import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.sequencing.SequenceToken;
import com.yaplabs.yapengine.sync.dlm.ChunkSyncDlm;
import com.yaplabs.yapengine.sync.lease.AtomicLeaseManager;
import net.jcip.annotations.ThreadSafe;

import java.util.Objects;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Thread 8 — Boundary Sync & Entity Handoff.
 * Receives cross-quad transactions, acquires leases via Thread 7 DLM,
 * then applies destination work on this thread (not on the DLM).
 */
@ThreadSafe
public final class BoundaryArbitrator implements Runnable {

    private static final Logger LOG = Logger.getLogger("YapEngine.Boundary");

    public record BoundaryTransaction(
            String entityId,
            String inventoryKey,
            SpatialQuadrant from,
            SpatialQuadrant to,
            SequenceToken token,
            Runnable applyOnDestination
    ) {
        public BoundaryTransaction {
            Objects.requireNonNull(entityId);
            Objects.requireNonNull(inventoryKey);
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
            Objects.requireNonNull(token);
            Objects.requireNonNull(applyOnDestination);
        }
    }

    private record GrantedWork(BoundaryTransaction tx, AtomicLeaseManager.Lease lease) {
    }

    private final ChunkSyncDlm dlm;
    private final LinkedTransferQueue<BoundaryTransaction> inbound = new LinkedTransferQueue<>();
    private final LinkedTransferQueue<GrantedWork> granted = new LinkedTransferQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong retried = new AtomicLong();
    private volatile Thread thread;

    public BoundaryArbitrator(ChunkSyncDlm dlm) {
        this.dlm = Objects.requireNonNull(dlm);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(this, "yap-t8-boundary-sync");
        thread.start();
        LOG.info("Boundary Sync online (Thread 8)");
    }

    public void stop() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
        }
    }

    public Thread getThread() {
        return thread;
    }

    public void submit(BoundaryTransaction tx) {
        inbound.offer(Objects.requireNonNull(tx));
    }

    public long getProcessed() {
        return processed.get();
    }

    public long getRetried() {
        return retried.get();
    }

    public int pending() {
        return inbound.size() + granted.size();
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                drainGranted();
                BoundaryTransaction tx = inbound.poll(20, TimeUnit.MILLISECONDS);
                if (tx != null) {
                    requestLease(tx);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException ex) {
                LOG.severe("Boundary fault: " + ex.getMessage());
            }
        }
        LOG.info("Boundary Sync shut down");
    }

    private void requestLease(BoundaryTransaction tx) {
        String owner = "yap-t8-boundary-sync";
        dlm.submit(ChunkSyncDlm.LeaseOp.acquire(
                tx.inventoryKey(),
                owner,
                lease -> granted.offer(new GrantedWork(tx, lease)),
                () -> {
                    retried.incrementAndGet();
                    inbound.offer(tx);
                }
        ));
    }

    private void drainGranted() {
        GrantedWork work;
        while ((work = granted.poll()) != null) {
            completeWithLease(work.tx(), work.lease());
        }
    }

    private void completeWithLease(BoundaryTransaction tx, AtomicLeaseManager.Lease lease) {
        try {
            tx.applyOnDestination().run();
            processed.incrementAndGet();
            LOG.fine(() -> "Boundary OK " + tx.entityId()
                    + " " + tx.from() + "→" + tx.to()
                    + " seq=" + tx.token().getStreamSeq()
                    + " µs=" + tx.token().getIngestMicros()
                    + " ageµs=" + tx.token().ageMicros());
        } catch (RuntimeException ex) {
            LOG.warning("Boundary apply failed " + tx.entityId() + ": " + ex.getMessage());
        } finally {
            tx.token().forget();
            dlm.submit(ChunkSyncDlm.LeaseOp.release(lease));
        }
    }
}

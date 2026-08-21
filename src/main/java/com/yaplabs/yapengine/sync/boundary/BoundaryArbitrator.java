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
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Logger;

/**
 * Thread 8 — Boundary Sync & Entity Handoff.
 * Receives cross-quad transactions, acquires leases via Thread 7 DLM,
 * then applies destination work on this thread (not on the DLM).
 * <p>
 * Critical latency rule: after {@link #requestLease}, wait on the <em>granted</em>
 * queue (and wake on deny→requeue). Never park on {@code inbound.poll} while a
 * lease is in flight — that used to cost up to ~20ms per border barrier and
 * stacked to ~60ms MSPT under high-pop bots on the origin border planes.
 */
@ThreadSafe
public final class BoundaryArbitrator implements Runnable {

    private static final Logger LOG = Logger.getLogger("YapEngine.Boundary");

    /** Idle wait when no inbound / granted work (interruptible via {@link #wake()}). */
    private static final long IDLE_PARK_NANOS = TimeUnit.MILLISECONDS.toNanos(20);
    /** Max wait for a single in-flight lease grant. */
    private static final long GRANT_WAIT_NS = TimeUnit.MILLISECONDS.toNanos(250);

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
    private final AtomicLong grantTimeouts = new AtomicLong();
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
        wake();
        if (thread != null) {
            thread.interrupt();
        }
    }

    public Thread getThread() {
        return thread;
    }

    public void submit(BoundaryTransaction tx) {
        inbound.offer(Objects.requireNonNull(tx));
        wake();
    }

    public long getProcessed() {
        return processed.get();
    }

    public long getRetried() {
        return retried.get();
    }

    public long getGrantTimeouts() {
        return grantTimeouts.get();
    }

    public int pending() {
        return inbound.size() + granted.size();
    }

    private void wake() {
        Thread t = thread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                drainGranted();
                BoundaryTransaction tx = inbound.poll();
                if (tx != null) {
                    requestLease(tx);
                    awaitGrantOrRetry();
                    continue;
                }
                // Idle: park until submit / grant / stop — do not block on inbound
                // alone while another queue may become ready.
                LockSupport.parkNanos(IDLE_PARK_NANOS);
            } catch (RuntimeException ex) {
                LOG.severe("Boundary fault: " + ex.getMessage());
            }
        }
        drainGranted();
        LOG.info("Boundary Sync shut down");
    }

    private void requestLease(BoundaryTransaction tx) {
        String owner = "yap-t8-boundary-sync";
        dlm.submit(ChunkSyncDlm.LeaseOp.acquire(
                tx.inventoryKey(),
                owner,
                lease -> {
                    granted.offer(new GrantedWork(tx, lease));
                    wake();
                },
                () -> {
                    retried.incrementAndGet();
                    inbound.offer(tx);
                    wake();
                }
        ));
    }

    /**
     * After submitting a lease acquire, wait for that grant (or a deny→inbound requeue).
     * Must not sleep on {@code inbound.poll} — grants arrive on {@code granted}.
     * Never abandon an in-flight lease: Paper main is blocked on the apply latch.
     */
    private void awaitGrantOrRetry() {
        long warnAfter = System.nanoTime() + GRANT_WAIT_NS;
        boolean warned = false;
        while (running.get()) {
            GrantedWork work = granted.poll();
            if (work != null) {
                completeWithLease(work.tx(), work.lease());
                drainGranted();
                return;
            }
            // Deny path re-queued to inbound — outer loop will requestLease again.
            if (!inbound.isEmpty()) {
                return;
            }
            if (!warned && System.nanoTime() >= warnAfter) {
                warned = true;
                grantTimeouts.incrementAndGet();
                LOG.warning("Boundary grant slow (>250ms); inbound=" + inbound.size()
                        + " granted=" + granted.size() + " dlmPending=" + dlm.pending());
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
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

package com.yaplabs.yapengine.sync.dlm;

import com.yaplabs.yapengine.sync.lease.AtomicLeaseManager;
import net.jcip.annotations.ThreadSafe;

import java.util.Objects;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Thread 7 — Chunk Sync DLM & Lease Manager.
 * Owns {@link AtomicLeaseManager}; grants / releases region leases and runs
 * atomic sector mutations off the spatial cores.
 */
@ThreadSafe
public final class ChunkSyncDlm implements Runnable {

    private static final Logger LOG = Logger.getLogger("YapEngine.DLM");

    public enum OpKind {
        ACQUIRE, RELEASE, SECTOR_MUTATION
    }

    public record LeaseOp(
            OpKind kind,
            String resourceKey,
            String owner,
            AtomicLeaseManager.Lease lease,
            Runnable mutation,
            Consumer<AtomicLeaseManager.Lease> onGranted,
            Runnable onDenied
    ) {
        public static LeaseOp acquire(String key, String owner,
                                      Consumer<AtomicLeaseManager.Lease> onGranted,
                                      Runnable onDenied) {
            return new LeaseOp(OpKind.ACQUIRE, key, owner, null, null, onGranted, onDenied);
        }

        public static LeaseOp release(AtomicLeaseManager.Lease lease) {
            Objects.requireNonNull(lease);
            return new LeaseOp(OpKind.RELEASE, lease.resourceKey(), lease.ownerThread(),
                    lease, null, null, null);
        }

        public static LeaseOp mutate(String key, String owner, Runnable mutation) {
            return new LeaseOp(OpKind.SECTOR_MUTATION, key, owner, null, mutation, null, null);
        }
    }

    private final AtomicLeaseManager leases = new AtomicLeaseManager();
    private final LinkedTransferQueue<LeaseOp> ops = new LinkedTransferQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong granted = new AtomicLong();
    private final AtomicLong denied = new AtomicLong();
    private final AtomicLong mutations = new AtomicLong();
    private volatile Thread thread;

    public AtomicLeaseManager leases() {
        return leases;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(this, "yap-t7-chunk-sync-dlm");
        thread.start();
        LOG.info("Chunk Sync DLM online (Thread 7)");
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

    public void submit(LeaseOp op) {
        ops.offer(Objects.requireNonNull(op));
    }

    public long grantedCount() {
        return granted.get();
    }

    public long deniedCount() {
        return denied.get();
    }

    public long mutationCount() {
        return mutations.get();
    }

    public int pending() {
        return ops.size();
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                LeaseOp op = ops.poll(50, TimeUnit.MILLISECONDS);
                if (op == null) {
                    continue;
                }
                dispatch(op);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException ex) {
                LOG.severe("DLM fault: " + ex.getMessage());
            }
        }
        LOG.info("Chunk Sync DLM shut down");
    }

    private void dispatch(LeaseOp op) {
        switch (op.kind()) {
            case ACQUIRE -> {
                AtomicLeaseManager.Lease lease = leases.tryAcquire(op.resourceKey(), op.owner());
                if (lease != null) {
                    granted.incrementAndGet();
                    if (op.onGranted() != null) {
                        op.onGranted().accept(lease);
                    }
                } else {
                    denied.incrementAndGet();
                    if (op.onDenied() != null) {
                        op.onDenied().run();
                    }
                }
            }
            case RELEASE -> {
                if (op.lease() != null) {
                    leases.release(op.lease());
                }
            }
            case SECTOR_MUTATION -> {
                AtomicLeaseManager.Lease lease = leases.tryAcquire(op.resourceKey(), op.owner());
                if (lease == null) {
                    ops.offer(op);
                    return;
                }
                try {
                    if (op.mutation() != null) {
                        op.mutation().run();
                    }
                    mutations.incrementAndGet();
                } finally {
                    leases.release(lease);
                }
            }
        }
    }
}

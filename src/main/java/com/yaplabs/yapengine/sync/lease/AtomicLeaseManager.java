package com.yaplabs.yapengine.sync.lease;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * CAS inventory/item leases — prevents dupe across spatial borders (Threads 7–8).
 */
@ThreadSafe
public final class AtomicLeaseManager {

    private static final Logger LOG = Logger.getLogger("YapEngine.Lease");

    public record Lease(String resourceKey, long leaseId, long grantedAtNanos, String ownerThread) {
    }

    private final ConcurrentHashMap<String, LeaseHolder> leases = new ConcurrentHashMap<>();
    private final AtomicLong leaseIds = new AtomicLong();
    private final long leaseTimeoutNanos;

    public AtomicLeaseManager() {
        this(TimeUnit.SECONDS.toNanos(5));
    }

    public AtomicLeaseManager(long leaseTimeoutNanos) {
        this.leaseTimeoutNanos = leaseTimeoutNanos;
    }

    public Lease tryAcquire(String resourceKey, String ownerThread) {
        Objects.requireNonNull(resourceKey, "resourceKey");
        Objects.requireNonNull(ownerThread, "ownerThread");
        expireIfStale(resourceKey);

        LeaseHolder holder = leases.computeIfAbsent(resourceKey, k -> new LeaseHolder());
        if (holder.locked.compareAndSet(false, true)) {
            long now = System.nanoTime();
            // Publish TTL start before any park/yield so expireIfStale cannot
            // treat grantedAtNanos==0 as ancient and steal the lock mid-grant.
            holder.grantedAtNanos = now;
            synchronized (holder) {
                long id = leaseIds.incrementAndGet();
                Lease lease = new Lease(resourceKey, id, now, ownerThread);
                holder.lease = lease;
                return lease;
            }
        }
        Lease current = holder.lease;
        if (current != null && ownerThread.equals(current.ownerThread())) {
            return current;
        }
        return null;
    }

    public boolean release(Lease lease) {
        if (lease == null) {
            return false;
        }
        LeaseHolder holder = leases.get(lease.resourceKey());
        if (holder == null) {
            return false;
        }
        synchronized (holder) {
            if (holder.lease != null && holder.lease.leaseId() == lease.leaseId()) {
                holder.lease = null;
                holder.locked.set(false);
                return true;
            }
        }
        return false;
    }

    public boolean isHeld(String resourceKey) {
        expireIfStale(resourceKey);
        LeaseHolder holder = leases.get(resourceKey);
        return holder != null && holder.locked.get();
    }

    /** Map entries including empty holders (months-long growth signal). */
    public int size() {
        return leases.size();
    }

    /** Drop holders that are unlocked and have no lease. */
    public int pruneEmpty() {
        int removed = 0;
        for (var e : leases.entrySet()) {
            LeaseHolder h = e.getValue();
            if (h != null && !h.locked.get() && h.lease == null) {
                if (leases.remove(e.getKey(), h)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    private void expireIfStale(String resourceKey) {
        LeaseHolder holder = leases.get(resourceKey);
        if (holder == null || !holder.locked.get()) {
            return;
        }
        // Grant still publishing — never expire an incomplete acquire.
        if (holder.lease == null) {
            return;
        }
        if ((System.nanoTime() - holder.grantedAtNanos) <= leaseTimeoutNanos) {
            return;
        }
        synchronized (holder) {
            if (holder.locked.get()
                    && holder.lease != null
                    && (System.nanoTime() - holder.grantedAtNanos) > leaseTimeoutNanos) {
                LOG.warning("Expiring stale lease on " + resourceKey);
                holder.lease = null;
                holder.locked.set(false);
            }
        }
    }

    private static final class LeaseHolder {
        private final AtomicBoolean locked = new AtomicBoolean(false);
        @GuardedBy("this")
        private volatile Lease lease;
        @GuardedBy("this")
        private volatile long grantedAtNanos;
    }
}

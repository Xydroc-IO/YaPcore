package com.yapcore.paper.phase3;

import com.yaplabs.yapengine.core.spatial.BitwiseQuadrantIndex;
import com.yaplabs.yapengine.core.spatial.ParallelGameCore;
import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.sequencing.SequenceToken;
import com.yaplabs.yapengine.sync.handoff.ChunkSyncLayer;
import com.yaplabs.yapengine.sync.lease.AtomicLeaseManager;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Phase 3 — fan a world tick across YapEngine spatial cores 3–6 and barrier-join.
 * Supports leased interior mutations on the spatial thread and border handoffs via T7/T8.
 */
public final class YapSpatialTickCoordinator {

    private static final Logger LOG = Logger.getLogger("YaPcore.Phase3.Tick");
    private static final long TICK_TIMEOUT_MS = 250;

    private final ParallelGameCore gameCore;
    private final ChunkSyncLayer syncLayer;
    private final AtomicBoolean online = new AtomicBoolean(false);
    private final AtomicLong ticks = new AtomicLong();
    private final AtomicLong overruns = new AtomicLong();
    private final AtomicLong tasksRun = new AtomicLong();
    private final AtomicLong leasedMutations = new AtomicLong();
    private final AtomicLong leaseDenies = new AtomicLong();
    private final AtomicLong borderHandoffs = new AtomicLong();

    public YapSpatialTickCoordinator(ParallelGameCore gameCore, ChunkSyncLayer syncLayer) {
        this.gameCore = Objects.requireNonNull(gameCore, "gameCore");
        this.syncLayer = Objects.requireNonNull(syncLayer, "syncLayer");
    }

    /** @deprecated use {@link #YapSpatialTickCoordinator(ParallelGameCore, ChunkSyncLayer)} */
    @Deprecated
    public YapSpatialTickCoordinator(ParallelGameCore gameCore) {
        this(gameCore, new ChunkSyncLayer());
    }

    public void start() {
        if (online.compareAndSet(false, true)) {
            LOG.info("YapSpatialTickCoordinator online — parallel tick fan-out to cores 3–6 (leased)");
        }
    }

    public void stop() {
        online.set(false);
        LOG.info("YapSpatialTickCoordinator stopped ticks=" + ticks.get()
                + " overruns=" + overruns.get()
                + " tasks=" + tasksRun.get()
                + " leased=" + leasedMutations.get()
                + " denies=" + leaseDenies.get()
                + " borders=" + borderHandoffs.get());
    }

    public boolean isOnline() {
        return online.get();
    }

    public ChunkSyncLayer syncLayer() {
        return syncLayer;
    }

    public long tickCount() {
        return ticks.get();
    }

    public long overrunCount() {
        return overruns.get();
    }

    public long leasedMutationCount() {
        return leasedMutations.get();
    }

    public long leaseDenyCount() {
        return leaseDenies.get();
    }

    public long borderHandoffCount() {
        return borderHandoffs.get();
    }

    /**
     * Run mutation on the <em>current</em> thread under a DLM lease (spatial cores 3–6).
     * Returns false if the lease could not be acquired.
     * <p>
     * Prefer {@link #runOwned(Runnable)} for interior same-quadrant batches — those are
     * exclusive by construction during {@link #runParallelTick} and skip CAS overhead.
     */
    public boolean runLeased(String resourceKey, Runnable mutation) {
        Objects.requireNonNull(resourceKey, "resourceKey");
        Objects.requireNonNull(mutation, "mutation");
        String owner = Thread.currentThread().getName();
        AtomicLeaseManager.Lease lease = syncLayer.leases().tryAcquire(resourceKey, owner);
        if (lease == null) {
            leaseDenies.incrementAndGet();
            return false;
        }
        try {
            mutation.run();
            leasedMutations.incrementAndGet();
            return true;
        } finally {
            syncLayer.leases().release(lease);
        }
    }

    /**
     * Run mutation on the current spatial thread with no DLM acquire.
     * Safe for interior work already partitioned by quadrant in {@link #runParallelTick}.
     */
    public void runOwned(Runnable mutation) {
        Objects.requireNonNull(mutation, "mutation");
        mutation.run();
        leasedMutations.incrementAndGet();
    }

    /** Chunk sector key used for leases. */
    public static String chunkKey(String worldName, int chunkX, int chunkZ) {
        return "c:" + worldName + ":" + chunkX + ":" + chunkZ;
    }

    /**
     * True when any of the 8 neighbors belongs to a different quadrant.
     * With sign-bit quadrants this is exactly the chunks on the {@code x=0} or
     * {@code z=0} plane ({@code chunkX/Z ∈ {-1, 0}}) — O(1), no 3×3 scan.
     */
    public static boolean isBorderChunk(int chunkX, int chunkZ) {
        return chunkX == -1 || chunkX == 0 || chunkZ == -1 || chunkZ == 0;
    }

    public void submitBorderHandoff(String entityId, String inventoryKey,
                                    SpatialQuadrant from, SpatialQuadrant to,
                                    Runnable applyOnDestination) {
        SequenceToken token = SequenceToken.next("border:" + entityId);
        syncLayer.submitHandoff(new ChunkSyncLayer.Handoff(
                entityId, inventoryKey, from, to, token, applyOnDestination));
        borderHandoffs.incrementAndGet();
    }

    /**
     * Run {@code mutation} on Thread 8 (boundary) after T7 grants a DLM lease on
     * {@code leaseKey}. Blocks the caller (Paper main) until apply completes —
     * same tick semantics as interior flush barriers.
     */
    public boolean runBorderTickSync(String leaseKey, Runnable mutation) {
        Objects.requireNonNull(leaseKey, "leaseKey");
        Objects.requireNonNull(mutation, "mutation");
        if (!online.get()) {
            mutation.run();
            return true;
        }
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        AtomicBoolean fault = new AtomicBoolean(false);
        // from/to are bookkeeping for handoff logs; lease is on leaseKey
        submitBorderHandoff(
                "border-tick:" + leaseKey,
                leaseKey,
                SpatialQuadrant.NW,
                SpatialQuadrant.SE,
                () -> {
                    try {
                        mutation.run();
                        leasedMutations.incrementAndGet();
                        ok.set(true);
                    } catch (Throwable t) {
                        fault.set(true);
                        LOG.log(Level.SEVERE, "Border tick fault key=" + leaseKey, t);
                    } finally {
                        done.countDown();
                    }
                });
        try {
            boolean finished = done.await(TICK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                overruns.incrementAndGet();
                done.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return ok.get() && !fault.get();
    }

    /**
     * Run one parallel tick: each quadrant’s work on its spatial core, then barrier.
     * Empty runnables are skipped. Returns false if the barrier timed out.
     * <p>
     * Always offloads to spatial cores 3–6 — including single-quadrant batches —
     * so Paper main does not pay entity/BE/tracker flush work inline.
     */
    public boolean runParallelTick(Map<SpatialQuadrant, Runnable> quadrantWork) {
        if (!online.get() || quadrantWork == null || quadrantWork.isEmpty()) {
            return true;
        }
        // Strip nulls
        EnumMap<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (var e : quadrantWork.entrySet()) {
            if (e.getValue() != null) {
                work.put(e.getKey(), e.getValue());
            }
        }
        if (work.isEmpty()) {
            return true;
        }
        CountDownLatch done = new CountDownLatch(work.size());
        AtomicBoolean failed = new AtomicBoolean(false);
        for (var e : work.entrySet()) {
            SpatialQuadrant q = e.getKey();
            Runnable task = e.getValue();
            gameCore.loop(q).executeUrgent(() -> {
                try {
                    task.run();
                    tasksRun.incrementAndGet();
                } catch (Throwable t) {
                    failed.set(true);
                    LOG.log(Level.SEVERE, "Phase 3 tick fault in " + q, t);
                } finally {
                    done.countDown();
                }
            });
        }
        boolean ok;
        try {
            ok = done.await(TICK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!ok) {
                // Soft overrun for metrics — but never abandon in-flight leased ticks
                // (racing Moonrise ChunkMap from spatial cores crashes the server).
                overruns.incrementAndGet();
                done.await();
                ok = true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        ticks.incrementAndGet();
        return ok && !failed.get();
    }

    /**
     * Run interior quadrant work and optional border work with <strong>one</strong> main-thread
     * barrier wait. Used by tracker flush when both interior and border sends are pending —
     * avoids paying {@link #runParallelTick} then {@link #runBorderTickSync} back-to-back
     * (high-pop / heavypop MSPT tax).
     */
    public boolean runParallelTickWithBorder(
            Map<SpatialQuadrant, Runnable> quadrantWork,
            String borderLeaseKey,
            Runnable borderMutation) {
        boolean hasInterior = quadrantWork != null && !quadrantWork.isEmpty();
        boolean hasBorder = borderMutation != null;
        if (!hasInterior && !hasBorder) {
            return true;
        }
        if (!hasBorder) {
            return runParallelTick(quadrantWork);
        }
        if (!hasInterior) {
            return runBorderTickSync(borderLeaseKey != null ? borderLeaseKey : "border", borderMutation);
        }
        if (!online.get()) {
            runParallelTick(quadrantWork);
            borderMutation.run();
            return true;
        }

        EnumMap<SpatialQuadrant, Runnable> work = new EnumMap<>(SpatialQuadrant.class);
        for (var e : quadrantWork.entrySet()) {
            if (e.getValue() != null) {
                work.put(e.getKey(), e.getValue());
            }
        }
        if (work.isEmpty()) {
            return runBorderTickSync(borderLeaseKey != null ? borderLeaseKey : "border", borderMutation);
        }

        CountDownLatch done = new CountDownLatch(work.size() + 1);
        AtomicBoolean failed = new AtomicBoolean(false);
        for (var e : work.entrySet()) {
            SpatialQuadrant q = e.getKey();
            Runnable task = e.getValue();
            gameCore.loop(q).executeUrgent(() -> {
                try {
                    task.run();
                    tasksRun.incrementAndGet();
                } catch (Throwable t) {
                    failed.set(true);
                    LOG.log(Level.SEVERE, "Phase 3 tick fault in " + q, t);
                } finally {
                    done.countDown();
                }
            });
        }
        String leaseKey = borderLeaseKey != null ? borderLeaseKey : "border";
        submitBorderHandoff(
                "border-tick:" + leaseKey,
                leaseKey,
                SpatialQuadrant.NW,
                SpatialQuadrant.SE,
                () -> {
                    try {
                        borderMutation.run();
                        leasedMutations.incrementAndGet();
                    } catch (Throwable t) {
                        failed.set(true);
                        LOG.log(Level.SEVERE, "Border tick fault key=" + leaseKey, t);
                    } finally {
                        done.countDown();
                    }
                });
        boolean ok;
        try {
            ok = done.await(TICK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!ok) {
                overruns.incrementAndGet();
                done.await();
                ok = true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        ticks.incrementAndGet();
        return ok && !failed.get();
    }

    /** Convenience: build a 4-way map (nulls allowed). */
    public boolean runParallelTick(Runnable nw, Runnable ne, Runnable sw, Runnable se) {
        EnumMap<SpatialQuadrant, Runnable> map = new EnumMap<>(SpatialQuadrant.class);
        if (nw != null) {
            map.put(SpatialQuadrant.NW, nw);
        }
        if (ne != null) {
            map.put(SpatialQuadrant.NE, ne);
        }
        if (sw != null) {
            map.put(SpatialQuadrant.SW, sw);
        }
        if (se != null) {
            map.put(SpatialQuadrant.SE, se);
        }
        return runParallelTick(map);
    }
}

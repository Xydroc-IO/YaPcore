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

    /** Chunk sector key used for leases. */
    public static String chunkKey(String worldName, int chunkX, int chunkZ) {
        return "c:" + worldName + ":" + chunkX + ":" + chunkZ;
    }

    /** True when any of the 8 neighbors belongs to a different quadrant. */
    public static boolean isBorderChunk(int chunkX, int chunkZ) {
        int self = BitwiseQuadrantIndex.quadrantId(
                BitwiseQuadrantIndex.packChunk(chunkX, chunkZ));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int other = BitwiseQuadrantIndex.quadrantId(
                        BitwiseQuadrantIndex.packChunk(chunkX + dx, chunkZ + dz));
                if (other != self) {
                    return true;
                }
            }
        }
        return false;
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
     * Run one parallel tick: each quadrant’s work on its spatial core, then barrier.
     * Empty runnables are skipped. Returns false if the barrier timed out.
     */
    public boolean runParallelTick(Map<SpatialQuadrant, Runnable> quadrantWork) {
        if (!online.get() || quadrantWork == null || quadrantWork.isEmpty()) {
            return true;
        }
        CountDownLatch done = new CountDownLatch(quadrantWork.size());
        AtomicBoolean failed = new AtomicBoolean(false);
        for (var e : quadrantWork.entrySet()) {
            SpatialQuadrant q = e.getKey();
            Runnable work = e.getValue();
            if (work == null) {
                done.countDown();
                continue;
            }
            SequenceToken token = SequenceToken.next("tick:" + q.name());
            gameCore.loop(q).executeUrgent(() -> {
                try {
                    work.run();
                    tasksRun.incrementAndGet();
                } catch (Throwable t) {
                    failed.set(true);
                    LOG.log(Level.SEVERE, "Phase 3 tick fault in " + q, t);
                } finally {
                    done.countDown();
                    token.forget();
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
        if (failed.get()) {
            return false;
        }
        return ok;
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

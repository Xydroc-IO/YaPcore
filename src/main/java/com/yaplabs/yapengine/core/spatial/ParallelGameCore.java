package com.yaplabs.yapengine.core.spatial;

import com.yaplabs.yapengine.bridge.CompatibilityBridge;
import com.yaplabs.yapengine.sequencing.SequenceToken;
import com.yaplabs.yapengine.sync.handoff.ChunkSyncLayer;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Threads 3–6 — Parallel Game Core with bitwise quadrant routing.
 */
public final class ParallelGameCore {

    private static final Logger LOG = Logger.getLogger("YapEngine.GameCore");

    private final QuadTreePartition partition = new QuadTreePartition();
    private final EnumMap<SpatialQuadrant, SpatialGameLoop> loops = new EnumMap<>(SpatialQuadrant.class);
    private final ChunkSyncLayer syncLayer;
    private final AtomicLong totalInventoryOps = new AtomicLong();

    public ParallelGameCore(ChunkSyncLayer syncLayer, CompatibilityBridge bridge) {
        this.syncLayer = syncLayer;
        for (SpatialQuadrant q : SpatialQuadrant.values()) {
            loops.put(q, new SpatialGameLoop(q, partition, syncLayer, bridge));
        }
    }

    public void start() {
        for (SpatialGameLoop loop : loops.values()) {
            loop.start();
        }
        LOG.info("Parallel Game Core online — 4 spatial threads (bitwise quadrant index)");
    }

    public void stop() {
        for (SpatialGameLoop loop : loops.values()) {
            loop.stop();
        }
    }

    public QuadTreePartition getPartition() {
        return partition;
    }

    public SpatialGameLoop loop(SpatialQuadrant q) {
        return loops.get(q);
    }

    public Map<SpatialQuadrant, SpatialGameLoop> loops() {
        return loops;
    }

    public long totalTicks() {
        long sum = 0;
        for (SpatialGameLoop loop : loops.values()) {
            sum += loop.getTickCounter();
        }
        return sum;
    }

    public long millisSinceAnyTick() {
        long maxStall = 0;
        for (SpatialGameLoop loop : loops.values()) {
            maxStall = Math.max(maxStall, loop.millisSinceLastTick());
        }
        return maxStall;
    }

    public long getInventoryOps() {
        long sum = totalInventoryOps.get();
        for (SpatialGameLoop loop : loops.values()) {
            sum += loop.getInventoryOps();
        }
        return sum;
    }

    public void dispatch(int blockX, int blockZ, SequenceToken token, String label, Runnable action) {
        SpatialQuadrant q = BitwiseQuadrantIndex.fromBlock(blockX, blockZ);
        loops.get(q).enqueue(new SpatialGameLoop.SequencedTask(token, action, label));
    }

    public void safeInventoryApply(String inventoryKey,
                                   int blockX,
                                   int blockZ,
                                   SequenceToken token,
                                   Runnable mutation) {
        SpatialQuadrant q = BitwiseQuadrantIndex.fromBlock(blockX, blockZ);
        SpatialGameLoop loop = loops.get(q);
        Runnable guarded = () -> {
            var lease = syncLayer.leases().tryAcquire(inventoryKey, Thread.currentThread().getName());
            if (lease == null) {
                syncLayer.submitHandoff(new ChunkSyncLayer.Handoff(
                        inventoryKey, inventoryKey, q, q, token,
                        () -> loop.applyInventoryMutation(inventoryKey, mutation)
                ));
                return;
            }
            try {
                loop.applyInventoryMutation(inventoryKey, mutation);
                totalInventoryOps.incrementAndGet();
            } finally {
                syncLayer.leases().release(lease);
            }
        };

        if (Thread.currentThread() == loop.getThread()) {
            guarded.run();
        } else {
            loop.enqueue(new SpatialGameLoop.SequencedTask(token, guarded, "inv:" + inventoryKey));
        }
    }
}

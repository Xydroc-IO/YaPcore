package com.yaplabs.yapengine.core.spatial;

import com.yaplabs.yapengine.bridge.CompatibilityBridge;
import com.yaplabs.yapengine.sequencing.SequenceToken;
import com.yaplabs.yapengine.sync.handoff.ChunkSyncLayer;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * One spatial 20 TPS loop (Threads 3–6).
 * Tasks are stored by global SequenceToken id and drained in ascending global
 * order each tick. Per-player gap-strict ordering is enforced upstream by
 * {@code InteractionSequencer} before tasks reach this loop.
 */
public final class SpatialGameLoop implements Runnable {

    public static final int TPS = 20;
    public static final long TICK_NANOS = 1_000_000_000L / TPS;

    public record SequencedTask(SequenceToken token, Runnable action, String label) {
    }

    private static final Logger LOG = Logger.getLogger("YapEngine.Spatial");

    private final SpatialQuadrant quadrant;
    private final QuadTreePartition partition;
    private final ChunkSyncLayer syncLayer;
    private final CompatibilityBridge bridge;
    private final ConcurrentSkipListMap<Long, SequencedTask> pending = new ConcurrentSkipListMap<>();
    /** Phase 3 tick fan-out — must not wait for the 20 TPS sleep cadence. */
    private final ConcurrentLinkedQueue<Runnable> urgent = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong tickCounter = new AtomicLong();
    private final AtomicLong lastTickNanos = new AtomicLong(System.nanoTime());
    private final AtomicLong inventoryOps = new AtomicLong();
    private volatile Thread thread;

    public SpatialGameLoop(SpatialQuadrant quadrant,
                           QuadTreePartition partition,
                           ChunkSyncLayer syncLayer,
                           CompatibilityBridge bridge) {
        this.quadrant = Objects.requireNonNull(quadrant);
        this.partition = partition;
        this.syncLayer = syncLayer;
        this.bridge = bridge;
    }

    public SpatialQuadrant getQuadrant() {
        return quadrant;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(this, quadrant.threadName());
        thread.start();
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

    public long getTickCounter() {
        return tickCounter.get();
    }

    public long millisSinceLastTick() {
        return (System.nanoTime() - lastTickNanos.get()) / 1_000_000L;
    }

    public long getInventoryOps() {
        return inventoryOps.get();
    }

    public void enqueue(SequencedTask task) {
        pending.put(task.token().getGlobalId(), task);
    }

    /**
     * Run {@code action} on this spatial thread ASAP (wakes the 20 TPS sleep).
     * Used by Phase 3 parallel tick so Paper main does not wait a full 50ms slot.
     */
    public void executeUrgent(Runnable action) {
        Objects.requireNonNull(action, "action");
        urgent.add(action);
        Thread t = thread;
        if (t != null) {
            t.interrupt();
        }
    }

    public void applyInventoryMutation(String label, Runnable action) {
        inventoryOps.incrementAndGet();
        action.run();
        LOG.info(() -> "[" + quadrant + "] inventory op: " + label
                + " (ops=" + inventoryOps.get() + ")");
    }

    @Override
    public void run() {
        LOG.info("Spatial loop " + quadrant + " online @ " + TPS + " TPS");
        while (running.get()) {
            long start = System.nanoTime();
            try {
                drainUrgent();
                runPhysicsTick();
                drainSequencedTasks();
                bridge.drainForTick(quadrant);
                tickCounter.incrementAndGet();
                lastTickNanos.set(System.nanoTime());

                long elapsed = System.nanoTime() - start;
                long sleep = TICK_NANOS - elapsed;
                // When idle, park longer so Paper main is not starved by 4×20Hz wakeups
                if (urgent.isEmpty() && pending.isEmpty() && sleep > 0) {
                    sleep = Math.max(sleep, 200_000_000L); // 200ms
                }
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                    } catch (InterruptedException e) {
                        // Urgent Phase 3 work — do not stop the loop
                        Thread.interrupted();
                        drainUrgent();
                    }
                }
            } catch (RuntimeException ex) {
                LOG.severe("Spatial " + quadrant + " fault: " + ex.getMessage());
            }
        }
        drainUrgent();
        LOG.info("Spatial loop " + quadrant + " stopped after " + tickCounter.get() + " ticks");
    }

    private void drainUrgent() {
        Runnable r;
        while ((r = urgent.poll()) != null) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                LOG.warning("Urgent task failed [" + quadrant + "]: " + ex.getMessage());
            }
        }
    }

    private void runPhysicsTick() {
        int entities = partition.entityCount(quadrant);
        if (tickCounter.get() % 40 == 0) {
            LOG.fine(() -> "[" + quadrant + "] physics entities=" + entities);
        }
    }

    private void drainSequencedTasks() {
        int n = 0;
        Map.Entry<Long, SequencedTask> entry;
        while ((entry = pending.pollFirstEntry()) != null) {
            SequencedTask task = entry.getValue();
            try {
                task.action().run();
                n++;
            } catch (RuntimeException ex) {
                LOG.warning("Task failed [" + task.label() + "]: " + ex.getMessage());
            } finally {
                task.token().forget();
            }
        }
        if (n > 0) {
            final int drained = n;
            LOG.fine(() -> "[" + quadrant + "] drained " + drained
                    + " tasks in µs-order");
        }
    }

    public void requestBorderHandoff(String entityId,
                                     String inventoryKey,
                                     SpatialQuadrant destination,
                                     SequenceToken token,
                                     Runnable applyOnDestination) {
        syncLayer.submitHandoff(new ChunkSyncLayer.Handoff(
                entityId, inventoryKey, quadrant, destination, token, applyOnDestination));
    }
}

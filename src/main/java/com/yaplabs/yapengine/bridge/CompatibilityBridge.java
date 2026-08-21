package com.yaplabs.yapengine.bridge;

import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.sequencing.SequenceToken;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Thread 9 — Compatibility Bridge.
 * Batches legacy plugin API mutations; drained at spatial tick end in µs order.
 */
public final class CompatibilityBridge implements Runnable {

    private static final Logger LOG = Logger.getLogger("YapEngine.Bridge");

    public record BridgedTask(
            SequenceToken token,
            String source,
            String description,
            SpatialQuadrant targetQuadrant,
            Runnable action
    ) {
        public BridgedTask {
            Objects.requireNonNull(token);
            Objects.requireNonNull(source);
            Objects.requireNonNull(description);
            Objects.requireNonNull(targetQuadrant);
            Objects.requireNonNull(action);
        }
    }

    private final ConcurrentLinkedQueue<BridgedTask> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong drained = new AtomicLong();
    private volatile Thread bridgeThread;

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        bridgeThread = new Thread(this, "yap-t9-compatibility-bridge");
        bridgeThread.start();
        LOG.info("Compatibility Bridge online (Thread 9)");
    }

    public void stop() {
        running.set(false);
        if (bridgeThread != null) {
            bridgeThread.interrupt();
        }
    }

    public Thread getBridgeThread() {
        return bridgeThread;
    }

    public void submit(String source, String description, SpatialQuadrant quadrant, Runnable action) {
        SequenceToken token = SequenceToken.next("bridge:" + source);
        BridgedTask task = new BridgedTask(token, source, description, quadrant, action);
        pending.offer(task);
        submitted.incrementAndGet();
        LOG.info(() -> "Bridge queued [" + source + "] " + description
                + " → " + quadrant
                + " seq=" + token.getSequenceId()
                + " µs=" + token.getIngestMicros());
    }

    public int drainForTick(SpatialQuadrant quadrant) {
        int count = 0;
        int size = pending.size();
        for (int i = 0; i < size; i++) {
            BridgedTask task = pending.poll();
            if (task == null) {
                break;
            }
            if (task.targetQuadrant() != quadrant) {
                pending.offer(task);
                continue;
            }
            try {
                task.action().run();
                drained.incrementAndGet();
                count++;
                task.token().forget();
                LOG.info(() -> "Bridge drained [" + task.source() + "] " + task.description()
                        + " on " + quadrant
                        + " µsAge=" + task.token().ageMicros());
            } catch (RuntimeException ex) {
                LOG.warning("Bridge task failed: " + ex.getMessage());
            }
        }
        return count;
    }

    public long getSubmitted() {
        return submitted.get();
    }

    public long getDrained() {
        return drained.get();
    }

    public int pendingCount() {
        return pending.size();
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOG.info("Compatibility Bridge shut down");
    }
}

package com.yapcore.bridge;

import com.yapcore.util.ThreadMetrics;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Thread 4 — Compatibility Bridge (The Synchronizer).
 * Intercepts legacy plugin API mutations and stages them as atomic runnables
 * for the Main Game Core to drain at the end of each tick.
 */
public class CompatibilityBridge implements Runnable {

    private static final Logger LOG = Logger.getLogger("YaPcore.Bridge");

    private final ConcurrentLinkedQueue<RunnableTask> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong drained = new AtomicLong();
    private volatile Thread bridgeThread;

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        bridgeThread = new Thread(this, "yap-core4-compatibility-bridge");
        bridgeThread.setDaemon(false);
        bridgeThread.start();
        ThreadMetrics.record("CompatibilityBridge", "started");
    }

    public void stop() {
        running.set(false);
        if (bridgeThread != null) {
            bridgeThread.interrupt();
        }
        ThreadMetrics.record("CompatibilityBridge", "stopped");
    }

    public Thread getBridgeThread() {
        return bridgeThread;
    }

    /**
     * Plugin-facing interceptor: packages a legacy world mutation for Core 3.
     */
    public void submitLegacyMutation(String source, String description, Runnable action) {
        Objects.requireNonNull(action, "action");
        RunnableTask task = new RunnableTask(source, description, action);
        pending.offer(task);
        submitted.incrementAndGet();
        ThreadMetrics.bump("CompatibilityBridge", "queued");
        LOG.fine(() -> "Staged legacy task from " + source + ": " + description);
    }

    /**
     * Called by GameCore at the tick handoff window. Drains all pending tasks
     * on the game thread — never while physics is mid-tick.
     *
     * @return number of tasks executed
     */
    public int drainForTick() {
        int count = 0;
        RunnableTask task;
        com.yapcore.api.threading.ThreadPools.enter(com.yapcore.api.Pool.SYNC, "CompatibilityBridge");
        try {
            while ((task = pending.poll()) != null) {
                try {
                    task.run();
                    drained.incrementAndGet();
                    count++;
                    ThreadMetrics.bump("CompatibilityBridge", "drained");
                } catch (RuntimeException ex) {
                    LOG.warning("Bridge task failed [" + task.description() + "]: " + ex.getMessage());
                }
            }
        } finally {
            com.yapcore.api.threading.ThreadPools.exit();
        }
        return count;
    }

    public int pendingCount() {
        return pending.size();
    }

    public long getSubmitted() {
        return submitted.get();
    }

    public long getDrained() {
        return drained.get();
    }

    @Override
    public void run() {
        LOG.info("Compatibility Bridge online — intercepting legacy API calls");
        while (running.get()) {
            try {
                // Coordinator idle loop: plugins push via submitLegacyMutation;
                // Core 3 drains. We only keep the channel alive and log depth.
                if (!pending.isEmpty()) {
                    ThreadMetrics.bump("CompatibilityBridge", "queue-observed");
                }
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOG.info("Compatibility Bridge shut down");
    }

    /**
     * Atomic, thread-safe staged work unit for the game core handoff queue.
     */
    public static final class RunnableTask implements Runnable {
        private final String source;
        private final String description;
        private final Runnable action;
        private final long createdAtNanos = System.nanoTime();

        public RunnableTask(String source, String description, Runnable action) {
            this.source = source;
            this.description = description;
            this.action = action;
        }

        public String source() {
            return source;
        }

        public String description() {
            return description;
        }

        public long createdAtNanos() {
            return createdAtNanos;
        }

        @Override
        public void run() {
            action.run();
        }
    }
}

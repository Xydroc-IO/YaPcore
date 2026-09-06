package com.yapcore.bridge;

import com.yapcore.util.ThreadMetrics;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Thin product API for Compatibility Bridge legacy mutation staging + metrics.
 *
 * <p><b>Production:</b> obtain via {@link com.yapcore.YaPcoreEngine#bridge()},
 * which returns a {@link ForwardingCompatibilityBridge} into the chassis
 * spatial bridge. Do not {@code new CompatibilityBridge()} for live lifecycle.
 *
 * <p>This base class keeps {@link #submitLegacyMutation} and metrics only.
 * There is no product GameCore drain and no standalone coordinator thread —
 * fallback submit (when Forwarding's chassis bridge is null) runs the action
 * immediately. {@link ForwardingCompatibilityBridge} overrides start/stop as
 * no-ops because YapEngine owns Thread 9.
 */
public class CompatibilityBridge {

    private static final Logger LOG = Logger.getLogger("YaPcore.Bridge");

    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong drained = new AtomicLong();

    /** No-op: production uses {@link ForwardingCompatibilityBridge}; chassis owns the thread. */
    public void start() {
        ThreadMetrics.record("CompatibilityBridge", "started-noop");
    }

    /** No-op: see {@link #start()}. */
    public void stop() {
        ThreadMetrics.record("CompatibilityBridge", "stopped-noop");
    }

    public Thread getBridgeThread() {
        return null;
    }

    /**
     * Plugin-facing interceptor: packages a legacy world mutation.
     * Base fallback executes immediately (no product GameCore tick drain).
     * {@link ForwardingCompatibilityBridge} forwards to the chassis spatial bridge.
     */
    public void submitLegacyMutation(String source, String description, Runnable action) {
        Objects.requireNonNull(action, "action");
        submitted.incrementAndGet();
        ThreadMetrics.bump("CompatibilityBridge", "queued");
        LOG.fine(() -> "Fallback legacy task from " + source + ": " + description);
        try {
            action.run();
            drained.incrementAndGet();
            ThreadMetrics.bump("CompatibilityBridge", "drained");
        } catch (RuntimeException ex) {
            LOG.warning("Bridge task failed [" + description + "]: " + ex.getMessage());
        }
    }

    /**
     * No product GameCore calls this. Kept for API symmetry;
     * {@link ForwardingCompatibilityBridge} returns 0 (chassis spatial loops drain).
     *
     * @return always 0 on the base type
     */
    public int drainForTick() {
        return 0;
    }

    public int pendingCount() {
        return 0;
    }

    public long getSubmitted() {
        return submitted.get();
    }

    public long getDrained() {
        return drained.get();
    }

    /**
     * Atomic staged work unit (API retained for callers that package mutations).
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

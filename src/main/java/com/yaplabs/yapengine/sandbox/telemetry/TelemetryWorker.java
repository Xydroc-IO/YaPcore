package com.yaplabs.yapengine.sandbox.telemetry;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Thread 16 — Async Worker / Telemetry.
 * Metrics snapshots, GC observation, low-priority console fan-out.
 */
public final class TelemetryWorker implements Runnable {

    private static final Logger LOG = Logger.getLogger("YapEngine.Telemetry");

    private final ConcurrentLinkedQueue<Runnable> jobs = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong snapshots = new AtomicLong();
    private final AtomicLong jobsDone = new AtomicLong();
    private volatile Thread thread;
    private volatile long lastGcCount;
    private volatile long lastGcTimeMs;

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        sampleGc();
        thread = new Thread(this, "yap-t16-telemetry");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
        LOG.info("Telemetry / Async Worker online (Thread 16)");
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

    public void offer(Runnable job) {
        if (job != null) {
            jobs.offer(job);
        }
    }

    public long snapshotCount() {
        return snapshots.get();
    }

    public long jobsDone() {
        return jobsDone.get();
    }

    public long lastGcCount() {
        return lastGcCount;
    }

    public long lastGcTimeMs() {
        return lastGcTimeMs;
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                Runnable job;
                while ((job = jobs.poll()) != null) {
                    try {
                        job.run();
                        jobsDone.incrementAndGet();
                    } catch (RuntimeException ex) {
                        LOG.fine("Telemetry job failed: " + ex.getMessage());
                    }
                }
                sampleGc();
                snapshots.incrementAndGet();
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOG.info("Telemetry shut down — snapshots=" + snapshots.get());
    }

    private void sampleGc() {
        long count = 0;
        long time = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = bean.getCollectionCount();
            long t = bean.getCollectionTime();
            if (c > 0) {
                count += c;
            }
            if (t > 0) {
                time += t;
            }
        }
        lastGcCount = count;
        lastGcTimeMs = time;
    }
}

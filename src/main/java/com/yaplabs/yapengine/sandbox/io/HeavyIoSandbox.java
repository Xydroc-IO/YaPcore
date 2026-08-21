package com.yaplabs.yapengine.sandbox.io;

import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Threads 12–15 — Heavy I/O Sandboxes with dedicated roles.
 */
public final class HeavyIoSandbox {

    private static final Logger LOG = Logger.getLogger("YapEngine.HeavyIO");

    private final EnumMap<HeavyIoRole, ExecutorService> pools = new EnumMap<>(HeavyIoRole.class);
    private final EnumMap<HeavyIoRole, AtomicLong> counts = new EnumMap<>(HeavyIoRole.class);

    public HeavyIoSandbox() {
        for (HeavyIoRole role : HeavyIoRole.values()) {
            pools.put(role, Executors.newSingleThreadExecutor(named(role)));
            counts.put(role, new AtomicLong());
        }
        LOG.info("Heavy I/O Sandboxes online (Threads 12–15: DB / world / packs / Bedrock)");
    }

    public void shutdown() {
        for (ExecutorService pool : pools.values()) {
            pool.shutdownNow();
        }
    }

    public void run(HeavyIoRole role, Runnable task) {
        Objects.requireNonNull(role);
        Objects.requireNonNull(task);
        counts.get(role).incrementAndGet();
        pools.get(role).execute(() -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                LOG.severe(role + " I/O failed: " + ex.getMessage());
            }
        });
    }

    public void runDatabase(Runnable task) {
        run(HeavyIoRole.DATABASE, task);
    }

    public void runWorldSave(Runnable task) {
        run(HeavyIoRole.WORLD_SAVE, task);
    }

    public void runResourcePack(Runnable task) {
        run(HeavyIoRole.RESOURCE_PACK, task);
    }

    public void runBedrock(Runnable task) {
        run(HeavyIoRole.BEDROCK, task);
    }

    /** Prefer database lane; used by generic async plugin IO. */
    public void runAny(Runnable task) {
        runDatabase(task);
    }

    public long taskCount(HeavyIoRole role) {
        return counts.get(role).get();
    }

    public long totalTasks() {
        long sum = 0;
        for (AtomicLong c : counts.values()) {
            sum += c.get();
        }
        return sum;
    }

    private static ThreadFactory named(HeavyIoRole role) {
        return r -> {
            Thread t = new Thread(r, role.threadName());
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        };
    }
}

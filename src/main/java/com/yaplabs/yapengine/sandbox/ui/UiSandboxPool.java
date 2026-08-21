package com.yaplabs.yapengine.sandbox.ui;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Threads 10–11 — UI Sandboxes (menus, inventory clicks, scoreboard/bossbar).
 */
public final class UiSandboxPool {

    private static final Logger LOG = Logger.getLogger("YapEngine.UI");

    private final ExecutorService pool;
    private final AtomicLong tasks = new AtomicLong();

    public UiSandboxPool() {
        this.pool = Executors.newFixedThreadPool(2, named());
        LOG.info("UI Sandboxes online (Threads 10–11)");
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    public void execute(Runnable task) {
        Objects.requireNonNull(task);
        tasks.incrementAndGet();
        pool.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                LOG.severe("UI task failed: " + ex.getMessage());
            }
        });
    }

    /** Thread 10 — menu / inventory click path. */
    public void runMenu(Runnable task) {
        execute(task);
    }

    /** Thread 11 — scoreboard / bossbar / custom UI state. */
    public void runHud(Runnable task) {
        execute(task);
    }

    public long taskCount() {
        return tasks.get();
    }

    private static ThreadFactory named() {
        AtomicInteger n = new AtomicInteger(10);
        return r -> {
            int id = n.getAndIncrement();
            String name = id == 10 ? "yap-t10-ui-menu" : "yap-t11-ui-hud";
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        };
    }
}

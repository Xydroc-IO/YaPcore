package com.yapcore.items.item;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Reloads the item registry when {@code items/} YAML changes on disk (fleet catalog sync
 * from another backend, dashboard push, or manual edit).
 */
public final class ItemCatalogWatcher {

    private final JavaPlugin plugin;
    private final Path itemsDir;
    private final Runnable reload;
    private final AtomicLong suppressUntilMs = new AtomicLong(0);
    private final AtomicBoolean reloadQueued = new AtomicBoolean(false);
    private volatile long lastFingerprint = -1L;
    private YapTask timer;

    public ItemCatalogWatcher(JavaPlugin plugin, Path itemsDir, Runnable reload) {
        this.plugin = plugin;
        this.itemsDir = itemsDir;
        this.reload = reload;
    }

    public void start() {
        lastFingerprint = fingerprint();
        timer = YapSched.globalTimer(plugin, this::tick, 40L, 40L);
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    /** Ignore disk churn from a local create/delete for a couple seconds. */
    public void suppressLocalWrite() {
        suppressUntilMs.set(System.currentTimeMillis() + 2500L);
        lastFingerprint = fingerprint();
    }

    private void tick() {
        if (System.currentTimeMillis() < suppressUntilMs.get()) {
            lastFingerprint = fingerprint();
            return;
        }
        long fp = fingerprint();
        if (lastFingerprint < 0) {
            lastFingerprint = fp;
            return;
        }
        if (fp == lastFingerprint) {
            return;
        }
        lastFingerprint = fp;
        if (!reloadQueued.compareAndSet(false, true)) {
            return;
        }
        YapSched.globalLater(plugin, () -> {
            try {
                reload.run();
                plugin.getLogger().info("YaPItems registry reloaded (catalog files changed on disk)");
            } finally {
                reloadQueued.set(false);
                lastFingerprint = fingerprint();
            }
        }, 5L);
    }

    private long fingerprint() {
        if (!Files.isDirectory(itemsDir)) {
            return 0L;
        }
        long hash = 1L;
        try (Stream<Path> walk = Files.walk(itemsDir, 3)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString().toLowerCase();
                if (!name.endsWith(".yml") && !name.endsWith(".yaml")) {
                    continue;
                }
                hash = 31L * hash + name.hashCode();
                hash = 31L * hash + Files.size(p);
                hash = 31L * hash + Files.getLastModifiedTime(p).toMillis();
            }
        } catch (IOException ignored) {
            return hash;
        }
        return hash;
    }
}

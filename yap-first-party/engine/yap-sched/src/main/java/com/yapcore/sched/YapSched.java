package com.yapcore.sched;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Folia-first scheduling for YaP first-party plugins.
 * <p>
 * Prefers Folia/Paper {@code GlobalRegionScheduler} / {@code AsyncScheduler} /
 * {@code EntityScheduler} / {@code RegionScheduler}. Falls back to
 * {@code BukkitScheduler} only when region schedulers are unavailable.
 */
public final class YapSched {

    private YapSched() {
    }

    public static boolean hasGlobalRegion() {
        try {
            return Bukkit.getGlobalRegionScheduler() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void global(Plugin plugin, Runnable task) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        if (hasGlobalRegion()) {
            Bukkit.getGlobalRegionScheduler().run(plugin, st -> task.run());
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static YapTask globalLater(Plugin plugin, Runnable task, long delayTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        if (hasGlobalRegion()) {
            return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, st -> task.run(), delay));
        }
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
    }

    public static YapTask globalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (hasGlobalRegion()) {
            return wrap(Bukkit.getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, st -> task.run(), delay, period));
        }
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
    }

    public static void async(Plugin plugin, Runnable task) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, st -> task.run());
        } catch (Throwable t) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static YapTask asyncLater(Plugin plugin, Runnable task, long delayTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        try {
            return wrap(Bukkit.getAsyncScheduler().runDelayed(plugin, st -> task.run(),
                    delay * 50L, java.util.concurrent.TimeUnit.MILLISECONDS));
        } catch (Throwable t) {
            return wrap(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay));
        }
    }

    public static YapTask asyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        try {
            return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, st -> task.run(),
                    delay * 50L, period * 50L, java.util.concurrent.TimeUnit.MILLISECONDS));
        } catch (Throwable t) {
            return wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period));
        }
    }

    /** Run on the entity's owning region (Folia-safe entity mutation). */
    public static void entity(Plugin plugin, Entity entity, Runnable task) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        try {
            entity.getScheduler().run(plugin, st -> task.run(), null);
        } catch (Throwable t) {
            global(plugin, task);
        }
    }

    public static YapTask entityLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        try {
            return wrap(entity.getScheduler().runDelayed(plugin, st -> task.run(), null, delay));
        } catch (Throwable t) {
            return globalLater(plugin, task, delay);
        }
    }

    /** Run at a world block location's region. */
    public static void region(Plugin plugin, Location loc, Runnable task) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(loc, "loc");
        Objects.requireNonNull(task, "task");
        World world = loc.getWorld();
        if (world == null) {
            global(plugin, task);
            return;
        }
        try {
            Bukkit.getRegionScheduler().execute(plugin, world, loc.getBlockX() >> 4, loc.getBlockZ() >> 4,
                    task);
        } catch (Throwable t) {
            global(plugin, task);
        }
    }

    public static void region(Plugin plugin, World world, int blockX, int blockZ, Runnable task) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        try {
            Bukkit.getRegionScheduler().execute(plugin, world, blockX >> 4, blockZ >> 4, task);
        } catch (Throwable t) {
            global(plugin, task);
        }
    }

    /** Run on the owning region for chunk coordinates (Folia-safe chunk/world mutation). */
    public static void regionChunk(Plugin plugin, World world, int chunkX, int chunkZ, Runnable task) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        try {
            Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, task);
        } catch (Throwable t) {
            global(plugin, task);
        }
    }

    public static YapTask regionChunkLater(Plugin plugin, World world, int chunkX, int chunkZ,
                                           Runnable task, long delayTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        try {
            return wrap(Bukkit.getRegionScheduler()
                    .runDelayed(plugin, world, chunkX, chunkZ, st -> task.run(), delay));
        } catch (Throwable t) {
            return globalLater(plugin, task, delay);
        }
    }

    /**
     * Fixed-rate task on the region owning {@code (chunkX, chunkZ)}.
     * On Folia, {@link org.bukkit.Server#getAverageTickTime()} is region-local —
     * MSPT benches must sample from the loaded region, not the global region.
     */
    public static YapTask regionChunkTimer(Plugin plugin, World world, int chunkX, int chunkZ,
                                           Runnable task, long delayTicks, long periodTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        try {
            return wrap(Bukkit.getRegionScheduler()
                    .runAtFixedRate(plugin, world, chunkX, chunkZ, st -> task.run(), delay, period));
        } catch (Throwable t) {
            return globalTimer(plugin, task, delay, period);
        }
    }

    /**
     * Folia: true when the server exposes region schedulers (Folia or modern Paper).
     * Prefer entity/region affinity for world mutations either way.
     */
    public static boolean isRegionized() {
        return hasGlobalRegion();
    }

    private static YapTask wrap(ScheduledTask task) {
        return new YapTask() {
            private final AtomicBoolean cancelled = new AtomicBoolean(false);

            @Override
            public void cancel() {
                if (cancelled.compareAndSet(false, true)) {
                    task.cancel();
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get() || task.isCancelled();
            }
        };
    }

    private static YapTask wrap(BukkitTask task) {
        return new YapTask() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }

    /** Adapt Consumer&lt;ScheduledTask&gt;-style APIs when callers already have a Consumer. */
    public static Consumer<ScheduledTask> asConsumer(Runnable task) {
        return st -> task.run();
    }
}

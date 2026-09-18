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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Folia-first scheduling for YaP first-party plugins.
 * <p>
 * Prefers Folia/Paper {@code GlobalRegionScheduler} / {@code AsyncScheduler} /
 * {@code EntityScheduler} / {@code RegionScheduler}. Falls back to
 * {@code BukkitScheduler} only when region schedulers are unavailable.
 *
 * <p>On Folia, {@link #entity} / {@link #region} never fall back to
 * {@link #global} — that would run world mutations on the global region.
 */
public final class YapSched {

    private static final Logger LOG = Logger.getLogger("YaP.Sched");
    private static final boolean FOLIA = detectFolia();

    private YapSched() {
    }

    static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.TickRegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** True when running under Folia region threads (not Paper's global-as-main). */
    public static boolean isFolia() {
        return FOLIA;
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
            entity.getScheduler().run(plugin, st -> task.run(),
                    () -> LOG.fine("YapSched.entity: entity retired"));
        } catch (Throwable t) {
            Location loc = safeLocation(entity);
            if (loc != null && loc.getWorld() != null) {
                region(plugin, loc, task);
                return;
            }
            worldMutationFailed("entity", plugin, task, t);
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
            Location loc = safeLocation(entity);
            if (loc != null && loc.getWorld() != null) {
                return regionChunkLater(plugin, loc.getWorld(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4,
                        task, delay);
            }
            return worldMutationFailedTask("entityLater", plugin, task, delay, null, t);
        }
    }

    /**
     * Fixed-rate task on the entity's scheduler (Folia-safe).
     * The callback receives the {@link YapTask} so it can cancel itself.
     */
    public static YapTask entityTimer(Plugin plugin, Entity entity,
                                      java.util.function.Consumer<YapTask> task,
                                      long delayTicks, long periodTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        DeferredYapTask wrapped = new DeferredYapTask();
        try {
            ScheduledTask scheduled = entity.getScheduler().runAtFixedRate(plugin, st -> {
                task.accept(wrapped);
            }, null, delay, period);
            wrapped.bind(scheduled);
            return wrapped;
        } catch (Throwable t) {
            if (!FOLIA) {
                YapTask paper = globalTimer(plugin, () -> task.accept(wrapped), delay, period);
                wrapped.bindBukkit(paper);
                return wrapped;
            }
            LOG.log(Level.SEVERE, "YapSched.entityTimer failed; dropped (not global region)", t);
            return dropped();
        }
    }

    /** Run at a world block location's region. */
    public static void region(Plugin plugin, Location loc, Runnable task) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(loc, "loc");
        Objects.requireNonNull(task, "task");
        World world = loc.getWorld();
        if (world == null) {
            worldMutationFailed("region", plugin, task, new IllegalStateException("location has no world"));
            return;
        }
        region(plugin, world, loc.getBlockX(), loc.getBlockZ(), task);
    }

    public static void region(Plugin plugin, World world, int blockX, int blockZ, Runnable task) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(task, "task");
        try {
            Bukkit.getRegionScheduler().execute(plugin, world, blockX >> 4, blockZ >> 4, task);
        } catch (Throwable t) {
            worldMutationFailed("region", plugin, task, t);
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
            worldMutationFailed("regionChunk", plugin, task, t);
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
            return worldMutationFailedTask("regionChunkLater", plugin, task, delay, null, t);
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
            return worldMutationFailedTask("regionChunkTimer", plugin, task, delay, period, t);
        }
    }

    /**
     * Folia: true when the server exposes region schedulers (Folia or modern Paper).
     * Prefer entity/region affinity for world mutations either way.
     */
    public static boolean isRegionized() {
        return hasGlobalRegion();
    }

    private static Location safeLocation(Entity entity) {
        try {
            return entity.getLocation();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void worldMutationFailed(String what, Plugin plugin, Runnable task, Throwable t) {
        if (!FOLIA) {
            global(plugin, task);
            return;
        }
        LOG.log(Level.SEVERE, "YapSched." + what + " failed; dropped (not global region)", t);
    }

    private static YapTask worldMutationFailedTask(String what, Plugin plugin, Runnable task,
                                                   long delayTicks, Long periodTicks, Throwable t) {
        if (!FOLIA) {
            if (periodTicks != null) {
                return globalTimer(plugin, task, delayTicks, periodTicks);
            }
            return globalLater(plugin, task, delayTicks);
        }
        LOG.log(Level.SEVERE, "YapSched." + what + " failed; dropped (not global region)", t);
        return dropped();
    }

    private static YapTask dropped() {
        return new YapTask() {
            @Override
            public void cancel() {
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        };
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

    /**
     * YapTask that exists before the Folia handle is bound, so the first timer
     * fire can cancel itself instead of seeing a null ref.
     */
    static final class DeferredYapTask implements YapTask {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile ScheduledTask scheduled;
        private volatile YapTask inner;

        void bind(ScheduledTask task) {
            scheduled = task;
            if (cancelled.get() && task != null) {
                task.cancel();
            }
        }

        void bindBukkit(YapTask task) {
            inner = task;
            if (cancelled.get() && task != null) {
                task.cancel();
            }
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                ScheduledTask st = scheduled;
                if (st != null) {
                    st.cancel();
                }
                YapTask wrapped = inner;
                if (wrapped != null) {
                    wrapped.cancel();
                }
            }
        }

        @Override
        public boolean isCancelled() {
            if (cancelled.get()) {
                return true;
            }
            ScheduledTask st = scheduled;
            if (st != null && st.isCancelled()) {
                return true;
            }
            YapTask wrapped = inner;
            return wrapped != null && wrapped.isCancelled();
        }
    }
}

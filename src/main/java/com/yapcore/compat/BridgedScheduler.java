package com.yapcore.compat;

import com.yapcore.api.Pool;
import com.yapcore.api.YaPScheduler;
import com.yapcore.api.threading.ThreadPools;
import com.yapcore.bridge.CompatibilityBridge;
import com.yapcore.plugin.PluginSandboxPool;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routes Bukkit sync tasks through the Compatibility Bridge and async tasks
 * onto the Heavy I/O pool (DB-safe). UI-sensitive async can use {@link YaPScheduler}.
 */
public final class BridgedScheduler implements BukkitScheduler, YaPScheduler {

    private static final long TICK_MS = 50L;

    private final CompatibilityBridge bridge;
    private final PluginSandboxPool pools;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, TaskHandle> tasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer =
            new ScheduledThreadPoolExecutor(2, r -> {
                Thread t = new Thread(r, "yap-scheduler-timer");
                t.setDaemon(true);
                return t;
            });

    public BridgedScheduler(CompatibilityBridge bridge, PluginSandboxPool pools) {
        this.bridge = bridge;
        this.pools = pools;
    }

    public void shutdown() {
        timer.shutdownNow();
        tasks.clear();
    }

    @Override
    public void run(Pool pool, Runnable task) {
        switch (pool) {
            case UI -> pools.submitUiTask(ThreadPools.wrap(Pool.UI, "YaPScheduler.UI", wrap(task)));
            case HEAVY -> pools.submitHeavyIo(ThreadPools.wrap(Pool.HEAVY, "YaPScheduler.HEAVY", wrap(task)));
            case SYNC -> bridge.submitLegacyMutation("YaPScheduler", "sync-task",
                    ThreadPools.wrap(Pool.SYNC, "YaPScheduler.SYNC", wrap(task)));
        }
    }

    @Override
    public void runLater(Pool pool, Runnable task, long delayMs) {
        timer.schedule(() -> run(pool, task), Math.max(0, delayMs), TimeUnit.MILLISECONDS);
    }

    @Override
    public BukkitTask runTask(Plugin plugin, Runnable task) {
        return schedule(plugin, task, true, 0, -1);
    }

    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task) {
        return schedule(plugin, task, false, 0, -1);
    }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        return schedule(plugin, task, true, delayTicks, -1);
    }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delayTicks) {
        return schedule(plugin, task, false, delayTicks, -1);
    }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return schedule(plugin, task, true, delayTicks, periodTicks);
    }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return schedule(plugin, task, false, delayTicks, periodTicks);
    }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, Runnable task, long delayTicks) {
        return runTaskLater(plugin, task, delayTicks).getTaskId();
    }

    @Override
    public int scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return runTaskTimer(plugin, task, delayTicks, periodTicks).getTaskId();
    }

    @Override
    public int scheduleAsyncDelayedTask(Plugin plugin, Runnable task, long delayTicks) {
        return runTaskLaterAsynchronously(plugin, task, delayTicks).getTaskId();
    }

    @Override
    public int scheduleAsyncRepeatingTask(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks).getTaskId();
    }

    @Override
    public void cancelTask(int taskId) {
        TaskHandle handle = tasks.remove(taskId);
        if (handle != null) {
            handle.cancel();
        }
    }

    @Override
    public void cancelTasks(Plugin plugin) {
        tasks.entrySet().removeIf(e -> {
            if (e.getValue().plugin == plugin) {
                e.getValue().cancel();
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean isCurrentlyRunning(int taskId) {
        return tasks.containsKey(taskId);
    }

    @Override
    public boolean isQueued(int taskId) {
        return tasks.containsKey(taskId);
    }

    private BukkitTask schedule(Plugin plugin, Runnable task, boolean sync, long delayTicks, long periodTicks) {
        int id = nextId.getAndIncrement();
        long delayMs = Math.max(0, delayTicks) * TICK_MS;
        Runnable body = wrap(task);
        ScheduledFuture<?> future;
        if (periodTicks > 0) {
            long periodMs = periodTicks * TICK_MS;
            future = timer.scheduleAtFixedRate(() -> dispatch(plugin, body, sync), delayMs, periodMs, TimeUnit.MILLISECONDS);
        } else {
            future = timer.schedule(() -> {
                dispatch(plugin, body, sync);
                tasks.remove(id);
            }, delayMs, TimeUnit.MILLISECONDS);
        }
        TaskHandle handle = new TaskHandle(id, plugin, sync, future);
        tasks.put(id, handle);
        return handle;
    }

    private void dispatch(Plugin plugin, Runnable body, boolean sync) {
        String source = plugin != null ? plugin.getName() : "scheduler";
        if (sync) {
            bridge.submitLegacyMutation(source, "bukkit-sync",
                    ThreadPools.wrap(Pool.SYNC, source + ":sync", body));
        } else {
            pools.submitHeavyIo(ThreadPools.wrap(Pool.HEAVY, source + ":async", body));
        }
    }

    private static Runnable wrap(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                java.util.logging.Logger.getLogger("YaPcore.Scheduler")
                        .log(java.util.logging.Level.SEVERE, "Scheduled task failed", t);
            }
        };
    }

    private final class TaskHandle implements BukkitTask {
        private final int id;
        private final Plugin plugin;
        private final boolean sync;
        private final ScheduledFuture<?> future;

        private TaskHandle(int id, Plugin plugin, boolean sync, ScheduledFuture<?> future) {
            this.id = id;
            this.plugin = plugin;
            this.sync = sync;
            this.future = future;
        }

        @Override
        public int getTaskId() { return id; }

        @Override
        public Plugin getOwner() { return plugin; }

        @Override
        public boolean isSync() { return sync; }

        @Override
        public void cancel() {
            future.cancel(false);
            tasks.remove(id);
        }
    }
}

package com.yapcore.sched;

/**
 * Cancel handle for {@link YapSched} tasks (Folia {@code ScheduledTask} or Bukkit {@code BukkitTask}).
 */
public interface YapTask {

    void cancel();

    boolean isCancelled();
}

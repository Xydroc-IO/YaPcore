package com.yapcore.smoke.legacy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Synthetic legacy Paper plugin: uses classic {@code Bukkit.getScheduler()} sync APIs
 * that Folia rejects unless {@code yap-sched-agent} is attached.
 *
 * <p>Declares {@code folia-supported: true} so Folia loads it; the point of the smoke
 * is scheduler translation, not the FoliaSupported gate.
 */
public final class LegacySchedSmokePlugin extends JavaPlugin {

    private BukkitTask timerTask;

    @Override
    public void onEnable() {
        getLogger().info("YaP-LEGACY-SCHED-SMOKE enable begin");

        BukkitTask immediate = Bukkit.getScheduler().runTask(this, () ->
                getLogger().info("YaP-LEGACY-SCHED-SMOKE runTask-ok"));

        BukkitTask later = Bukkit.getScheduler().runTaskLater(this, () ->
                getLogger().info("YaP-LEGACY-SCHED-SMOKE runTaskLater-ok"), 2L);

        timerTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            int n;

            @Override
            public void run() {
                n++;
                if (n == 1) {
                    getLogger().info("YaP-LEGACY-SCHED-SMOKE runTaskTimer-ok");
                }
                if (n >= 2 && timerTask != null) {
                    timerTask.cancel();
                }
            }
        }, 1L, 5L);

        getLogger().info("YaP-LEGACY-SCHED-SMOKE scheduled ids immediate="
                + immediate.getTaskId()
                + " later=" + later.getTaskId()
                + " timer=" + timerTask.getTaskId());

        int syncId = Bukkit.getScheduler().scheduleSyncDelayedTask(this, () ->
                getLogger().info("YaP-LEGACY-SCHED-SMOKE scheduleSyncDelayed-ok"), 3L);
        getLogger().info("YaP-LEGACY-SCHED-SMOKE scheduleSyncDelayed id=" + syncId);

        Bukkit.getScheduler().runTaskLater(this, () ->
                getLogger().info("YaP-LEGACY-SCHED-SMOKE all-ok"), 40L);
    }
}

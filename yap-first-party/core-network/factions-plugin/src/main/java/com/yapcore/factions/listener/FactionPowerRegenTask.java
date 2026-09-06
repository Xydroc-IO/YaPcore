package com.yapcore.factions.listener;

import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.service.FactionServiceImpl;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class FactionPowerRegenTask implements Runnable {

    private final JavaPlugin plugin;
    private final FactionsConfig config;
    private final FactionServiceImpl factions;
    private YapTask task;

    public FactionPowerRegenTask(JavaPlugin plugin, FactionsConfig config, FactionServiceImpl factions) {
        this.plugin = plugin;
        this.config = config;
        this.factions = factions;
    }

    public void start() {
        stop();
        long period = Math.max(20L, config.powerRegenIntervalTicks());
        task = YapSched.asyncTimer(plugin, this, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        factions.regenPowerTick();
    }
}

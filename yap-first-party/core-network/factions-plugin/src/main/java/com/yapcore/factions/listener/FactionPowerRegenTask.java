package com.yapcore.factions.listener;

import com.yapcore.factions.FactionsConfig;
import com.yapcore.factions.service.FactionServiceImpl;
import com.yapcore.sched.YapSched;
import org.bukkit.plugin.java.JavaPlugin;

public final class FactionPowerRegenTask implements Runnable {

    private final JavaPlugin plugin;
    private final FactionsConfig config;
    private final FactionServiceImpl factions;

    public FactionPowerRegenTask(JavaPlugin plugin, FactionsConfig config, FactionServiceImpl factions) {
        this.plugin = plugin;
        this.config = config;
        this.factions = factions;
    }

    public void start() {
        long period = Math.max(20L, config.powerRegenIntervalTicks());
        YapSched.asyncTimer(plugin, this, period, period);
    }

    @Override
    public void run() {
        factions.regenPowerTick();
    }
}

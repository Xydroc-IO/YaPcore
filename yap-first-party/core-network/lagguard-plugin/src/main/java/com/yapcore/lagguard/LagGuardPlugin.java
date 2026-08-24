package com.yapcore.lagguard;

import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LagGuardPlugin extends JavaPlugin {

    private LagGuardConfig config;
    private ChunkBudgetTracker tracker;
    private LagGuardServiceImpl service;
    private LagGuardListener listener;
    private LagGuardCommands commands;
    private YapTask statsTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        tracker = new ChunkBudgetTracker();
        config = new LagGuardConfig(this);
        config.reload();
        service = new LagGuardServiceImpl(tracker, config);
        listener = new LagGuardListener(this, config, tracker);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getServicesManager().register(LagGuardService.class, service, this, ServicePriority.Normal);

        PluginCommand cmd = getCommand("yaplagguard");
        if (cmd != null) {
            commands = new LagGuardCommands(this, config, tracker);
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        startStatsWriter();
        getLogger().info("YaPLagGuard ready — entities/chunk≤" + config.maxEntitiesPerChunk()
                + " tnt≤" + config.maxPrimedTntPerChunk()
                + " (YapSched regionized=" + YapSched.isRegionized() + ")");
    }

    public void reloadLagGuard() {
        config.reload();
        service.setConfig(config);
        if (listener != null) {
            listener.setConfig(config);
        }
        if (commands != null) {
            commands.setConfig(config);
        }
        startStatsWriter();
    }

    private void startStatsWriter() {
        if (statsTask != null) {
            statsTask.cancel();
            statsTask = null;
        }
        int period = config.statsWriteIntervalTicks();
        statsTask = YapSched.globalTimer(this, this::writeStatsJson, period, period);
    }

    private void writeStatsJson() {
        Path file = getDataFolder().toPath().resolve("stats.json");
        try {
            Files.createDirectories(file.getParent());
            String json = "{\n"
                    + "  \"enabled\": " + config.enabled() + ",\n"
                    + "  \"trips\": " + tracker.trips() + ",\n"
                    + "  \"entitiesCancelled\": " + tracker.entitiesCancelled() + ",\n"
                    + "  \"tntCancelled\": " + tracker.tntCancelled() + ",\n"
                    + "  \"hopperThrottled\": " + tracker.hopperThrottled() + ",\n"
                    + "  \"redstoneThrottled\": " + tracker.redstoneThrottled() + ",\n"
                    + "  \"maxEntitiesPerChunk\": " + config.maxEntitiesPerChunk() + ",\n"
                    + "  \"maxPrimedTntPerChunk\": " + config.maxPrimedTntPerChunk() + "\n"
                    + "}\n";
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().fine("stats write failed: " + e.getMessage());
        }
    }

    public ChunkBudgetTracker tracker() {
        return tracker;
    }

    public LagGuardConfig lagConfig() {
        return config;
    }

    @Override
    public void onDisable() {
        if (statsTask != null) {
            statsTask.cancel();
            statsTask = null;
        }
        if (service != null) {
            getServer().getServicesManager().unregister(LagGuardService.class, service);
        }
        writeStatsJson();
    }
}

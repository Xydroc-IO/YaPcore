package com.yapcore.lagguard;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class LagGuardConfig {

    private final JavaPlugin plugin;
    private boolean enabled = true;
    private int maxEntitiesPerChunk = 80;
    private int maxPrimedTntPerChunk = 12;
    private int maxHopperTransfersPerWindow = 64;
    private int hopperWindowTicks = 20;
    private int maxRedstoneEventsPerWindow = 128;
    private int redstoneWindowTicks = 20;
    private int statsWriteIntervalTicks = 100;
    private boolean logTrips = true;

    public LagGuardConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        maxEntitiesPerChunk = Math.max(8, c.getInt("max-entities-per-chunk", 80));
        maxPrimedTntPerChunk = Math.max(1, c.getInt("max-primed-tnt-per-chunk", 12));
        maxHopperTransfersPerWindow = Math.max(1, c.getInt("max-hopper-transfers-per-window", 64));
        hopperWindowTicks = Math.max(1, c.getInt("hopper-window-ticks", 20));
        maxRedstoneEventsPerWindow = Math.max(1, c.getInt("max-redstone-events-per-window", 128));
        redstoneWindowTicks = Math.max(1, c.getInt("redstone-window-ticks", 20));
        statsWriteIntervalTicks = Math.max(20, c.getInt("stats-write-interval-ticks", 100));
        logTrips = c.getBoolean("log-trips", true);
    }

    public boolean enabled() {
        return enabled;
    }

    public int maxEntitiesPerChunk() {
        return maxEntitiesPerChunk;
    }

    public int maxPrimedTntPerChunk() {
        return maxPrimedTntPerChunk;
    }

    public int maxHopperTransfersPerWindow() {
        return maxHopperTransfersPerWindow;
    }

    public int hopperWindowTicks() {
        return hopperWindowTicks;
    }

    public int maxRedstoneEventsPerWindow() {
        return maxRedstoneEventsPerWindow;
    }

    public int redstoneWindowTicks() {
        return redstoneWindowTicks;
    }

    public int statsWriteIntervalTicks() {
        return statsWriteIntervalTicks;
    }

    public boolean logTrips() {
        return logTrips;
    }
}

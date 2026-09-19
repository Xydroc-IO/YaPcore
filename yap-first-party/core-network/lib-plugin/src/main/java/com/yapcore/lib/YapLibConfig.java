package com.yapcore.lib;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YapLibConfig {

    private final JavaPlugin plugin;
    private boolean intercept = true;
    private boolean debugListeners;

    public YapLibConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        intercept = c.getBoolean("intercept", true);
        debugListeners = c.getBoolean("debug-listeners", false);
    }

    public boolean intercept() {
        return intercept;
    }

    public boolean debugListeners() {
        return debugListeners;
    }
}

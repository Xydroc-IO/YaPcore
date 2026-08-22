package com.yapcore.regions;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionsConfig {

    private final JavaPlugin plugin;
    private String serverId = "default";

    public RegionsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        serverId = c.getString("server-id", "default");
    }

    public String serverId() {
        return serverId;
    }
}

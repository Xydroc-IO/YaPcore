package com.yapcore.regions;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionsConfig {

    private final JavaPlugin plugin;
    private String serverId = "default";

    public RegionsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Fixed server id for unit tests (no Bukkit plugin). */
    public RegionsConfig(String serverId) {
        this.plugin = null;
        this.serverId = serverId == null || serverId.isBlank() ? "default" : serverId.trim();
    }

    public void reload() {
        if (plugin == null) {
            return;
        }
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        serverId = c.getString("server-id", "default");
    }

    public String serverId() {
        return serverId;
    }
}

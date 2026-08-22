package com.yapcore.npcs;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class NpcsConfig {

    private final JavaPlugin plugin;
    private String serverId = "default";
    private String defaultDialogue = "Hello, traveler!";

    public NpcsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        serverId = c.getString("server-id", "default");
        defaultDialogue = color(c.getString("dialogue.default", "&7Hello, traveler!"));
    }

    public String serverId() {
        return serverId;
    }

    public String defaultDialogue() {
        return defaultDialogue;
    }

    private static String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }
}

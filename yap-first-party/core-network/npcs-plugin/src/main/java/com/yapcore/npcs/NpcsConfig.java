package com.yapcore.npcs;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;

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
        if (serverId == null || serverId.isBlank() || "default".equalsIgnoreCase(serverId)) {
            String hinted = readInstanceServerId();
            if (hinted != null && !hinted.isBlank()) {
                plugin.getLogger().info("YaPNpcs server-id default → " + hinted
                        + " (fleet yap-server-id.txt)");
                serverId = hinted;
            }
        }
        defaultDialogue = color(c.getString("dialogue.default", "&7Hello, traveler!"));
    }

    /** Fleet instances stamp {@code yap-server-id.txt}; seed YAML often stays {@code default}. */
    private String readInstanceServerId() {
        try {
            Path hint = instanceServerIdHint(plugin.getDataFolder().toPath());
            if (hint != null && Files.isRegularFile(hint)) {
                String line = Files.readString(hint).trim();
                int nl = line.indexOf('\n');
                return nl < 0 ? line.trim() : line.substring(0, nl).trim();
            }
        } catch (Exception ignored) {
            // keep YAML value
        }
        return null;
    }

    /**
     * {@code plugins/YaPNpcs} → instance root. Must absolutize: a relative data folder
     * has a null grandparent, so the hint is skipped and hub shops never load.
     */
    static Path instanceServerIdHint(Path dataFolder) {
        if (dataFolder == null) {
            return null;
        }
        Path abs = dataFolder.toAbsolutePath().normalize();
        Path plugins = abs.getParent();
        if (plugins == null) {
            return null;
        }
        Path instance = plugins.getParent();
        if (instance == null) {
            return null;
        }
        return instance.resolve("yap-server-id.txt");
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

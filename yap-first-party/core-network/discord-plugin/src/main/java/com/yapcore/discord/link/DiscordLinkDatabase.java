package com.yapcore.discord.link;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

/**
 * Opens the link repository: YaPDB when available, else local SQLite.
 */
public final class DiscordLinkDatabase implements AutoCloseable {

    private final JavaPlugin plugin;
    private AutoCloseable backend;
    private DiscordLinkRepository repository;
    private boolean usingShared;

    public DiscordLinkDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(boolean preferShared) throws SQLException {
        close();
        if (preferShared && Bukkit.getPluginManager().getPlugin("YaPDB") != null) {
            try {
                DiscordLinkSharedFactory.SharedStore shared = DiscordLinkSharedFactory.open(plugin);
                backend = shared;
                repository = shared.repository();
                usingShared = true;
                return;
            } catch (Throwable t) {
                plugin.getLogger().info("YaPDB link open failed — falling back to SQLite: " + t.getMessage());
            }
        }
        DiscordLinkSqliteStore sqlite = DiscordLinkSqliteStore.open(plugin);
        backend = sqlite;
        repository = sqlite.repository();
        usingShared = false;
    }

    public DiscordLinkRepository repository() {
        return repository;
    }

    public boolean usingShared() {
        return usingShared;
    }

    @Override
    public void close() {
        if (backend != null) {
            try {
                backend.close();
            } catch (Exception e) {
                plugin.getLogger().fine("link store close: " + e.getMessage());
            }
            backend = null;
        }
        repository = null;
        usingShared = false;
    }
}

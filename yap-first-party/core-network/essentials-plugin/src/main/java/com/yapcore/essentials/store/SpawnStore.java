package com.yapcore.essentials.store;

import com.yapcore.essentials.EssentialsConfig;
import com.yapcore.essentials.db.EssentialsDatabase;
import com.yapcore.sched.YapSched;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

public final class SpawnStore {

    private final JavaPlugin plugin;
    private final EssentialsConfig config;
    private final EssentialsDatabase database;
    private final AtomicReference<Location> cached = new AtomicReference<>();

    public SpawnStore(JavaPlugin plugin, EssentialsConfig config, EssentialsDatabase database) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
    }

    public void load() {
        cached.set(config.fileSpawn());
        if (!config.spawnPersistDb() || !database.isOpen()) {
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                var opt = database.loadSpawn(config.spawnScopeKey());
                if (opt.isPresent()) {
                    cached.set(opt.get());
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load spawn from DB: " + e.getMessage());
            }
        });
    }

    public Location spawn() {
        Location loc = cached.get();
        return loc == null ? null : loc.clone();
    }

    public void setSpawn(Location location) {
        Location copy = location.clone();
        cached.set(copy);
        config.saveFileSpawn(copy);
        if (!config.spawnPersistDb() || !database.isOpen()) {
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                database.saveSpawn(config.spawnScopeKey(), copy);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save spawn to DB: " + e.getMessage());
            }
        });
    }
}

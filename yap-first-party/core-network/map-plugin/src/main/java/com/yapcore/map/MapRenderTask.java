package com.yapcore.map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class MapRenderTask implements Runnable {

    private final JavaPlugin plugin;
    private final MapConfig config;
    private final TileRenderer renderer;

    public MapRenderTask(JavaPlugin plugin, MapConfig config, TileRenderer renderer) {
        this.plugin = plugin;
        this.config = config;
        this.renderer = renderer;
    }

    @Override
    public void run() {
        for (String worldName : config.worlds()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Map world not loaded: " + worldName);
                continue;
            }
            renderer.renderWorld(plugin, world);
        }
    }
}

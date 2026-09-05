package com.yapcore.map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;

public final class MapRenderTask implements Runnable {

    private final JavaPlugin plugin;
    private final MapConfig config;
    private final TileRenderer renderer;
    private final ChunkMeshRenderer meshRenderer;

    public MapRenderTask(JavaPlugin plugin, MapConfig config, TileRenderer renderer) {
        this(plugin, config, renderer, null);
    }

    public MapRenderTask(JavaPlugin plugin, MapConfig config, TileRenderer renderer,
                         ChunkMeshRenderer meshRenderer) {
        this.plugin = plugin;
        this.config = config;
        this.renderer = renderer;
        this.meshRenderer = meshRenderer;
    }

    @Override
    public void run() {
        MapTelemetry.setStatus("rendering");
        int dirty = renderer.dirtyCount();
        if (meshRenderer != null) {
            dirty = Math.max(dirty, meshRenderer.dirtyCount());
        }
        MapTelemetry.updateDirty(dirty);
        for (String worldName : config.worlds()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Map world not loaded: " + worldName);
                continue;
            }
            renderer.renderWorld(plugin, world);
            if (meshRenderer != null && config.meshEnabled()) {
                meshRenderer.renderWorld(plugin, world);
            }
        }
        int remaining = renderer.dirtyCount();
        if (meshRenderer != null) {
            remaining = Math.max(remaining, meshRenderer.dirtyCount());
        }
        Path meshes = meshRenderer != null ? meshRenderer.meshesRoot() : null;
        MapTelemetry.markRenderComplete(renderer.tilesRoot(), meshes, remaining);
    }
}

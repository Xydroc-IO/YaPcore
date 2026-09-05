package com.yapcore.map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/** Marks map sample chunks dirty for incremental tile + mesh re-render. */
public final class MapDirtyListener implements Listener {

    private volatile TileRenderer renderer;
    private volatile ChunkMeshRenderer meshRenderer;

    public MapDirtyListener(TileRenderer renderer) {
        this(renderer, null);
    }

    public MapDirtyListener(TileRenderer renderer, ChunkMeshRenderer meshRenderer) {
        this.renderer = renderer;
        this.meshRenderer = meshRenderer;
    }

    public void setRenderers(TileRenderer renderer, ChunkMeshRenderer meshRenderer) {
        this.renderer = renderer;
        this.meshRenderer = meshRenderer;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        mark(event.getBlock().getWorld().getName(),
                event.getBlock().getX() >> 4,
                event.getBlock().getZ() >> 4);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        mark(event.getBlock().getWorld().getName(),
                event.getBlock().getX() >> 4,
                event.getBlock().getZ() >> 4);
    }

    private void mark(String world, int chunkX, int chunkZ) {
        TileRenderer tiles = renderer;
        if (tiles != null) {
            tiles.markDirty(world, chunkX, chunkZ);
        }
        ChunkMeshRenderer meshes = meshRenderer;
        if (meshes != null) {
            meshes.markDirty(world, chunkX, chunkZ);
        }
    }
}

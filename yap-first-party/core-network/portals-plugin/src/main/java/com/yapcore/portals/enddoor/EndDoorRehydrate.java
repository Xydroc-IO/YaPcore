package com.yapcore.portals.enddoor;

import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

/**
 * Re-arm End doors after restart / chunk reload without requiring another eye click.
 * Prefers {@link EndDoorStore} (YAML), then block PDC, then geometry + END_PORTAL_FRAME.
 */
public final class EndDoorRehydrate {

    private EndDoorRehydrate() {
    }

    public static void scanLoaded(
            JavaPlugin plugin, EndDoorStructure structure, EndDoorTags tags, EndDoorStore store) {
        for (EndDoorStructure.Frame frame : store.framesInLoadedWorlds()) {
            Block key = frame.keystone();
            if (!frame.world().isChunkLoaded(key.getX() >> 4, key.getZ() >> 4)) {
                continue;
            }
            apply(plugin, structure, tags, store, frame);
        }
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                rehydrateChunk(plugin, structure, tags, store, chunk);
            }
        }
    }

    public static void rehydrateChunk(
            JavaPlugin plugin,
            EndDoorStructure structure,
            EndDoorTags tags,
            EndDoorStore store,
            Chunk chunk) {
        World world = chunk.getWorld();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        YapSched.regionChunk(plugin, world, cx, cz, () -> {
            if (!world.isChunkLoaded(cx, cz)) {
                return;
            }
            store.frameAt(world, cx, cz).ifPresent(frame ->
                    apply(plugin, structure, tags, store, frame));

            Chunk live = world.getChunkAt(cx, cz);
            for (BlockState state : live.getTileEntities()) {
                Block block = state.getBlock();
                if (block.getType() != Material.END_PORTAL_FRAME) {
                    continue;
                }
                if (tags.isDungeonKeystone(block)) {
                    continue;
                }
                Optional<EndDoorStructure.Frame> fromPdc = tags.frameFromKeystone(block);
                if (fromPdc.isPresent()) {
                    apply(plugin, structure, tags, store, fromPdc.get());
                    continue;
                }
                structure.findCompleteFrame(block).ifPresent(frame -> {
                    Block key = frame.keystone();
                    if (key.getX() == block.getX()
                            && key.getY() == block.getY()
                            && key.getZ() == block.getZ()) {
                        apply(plugin, structure, tags, store, frame);
                    }
                });
            }
        });
    }

    public static void apply(
            JavaPlugin plugin,
            EndDoorStructure structure,
            EndDoorTags tags,
            EndDoorStore store,
            EndDoorStructure.Frame frame) {
        int cx = frame.keystone().getX() >> 4;
        int cz = frame.keystone().getZ() >> 4;
        YapSched.regionChunk(plugin, frame.world(), cx, cz, () -> {
            if (!EndDoorRegistry.contains(frame)) {
                EndDoorRegistry.register(frame);
            }
            store.upsert(frame);
            UUID owner = tags.owner(frame.keystone()).orElse(new UUID(0L, 0L));
            tags.installKeystone(frame, owner);
            // Always re-arm walkability + blue swirl (restarts / OOM drop ItemDisplays)
            structure.ensureWalkable(frame);
        });
    }
}

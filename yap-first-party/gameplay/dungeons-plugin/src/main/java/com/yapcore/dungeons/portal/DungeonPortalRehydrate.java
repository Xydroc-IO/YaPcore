package com.yapcore.dungeons.portal;

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

/** Re-arm dungeon structure portals after restart without re-eyeing. */
public final class DungeonPortalRehydrate {

    private DungeonPortalRehydrate() {
    }

    public static void scanLoaded(
            JavaPlugin plugin, PortalStructure structure, PortalStructureTags tags, DungeonPortalStore store) {
        for (PortalStructure.Frame frame : store.framesInLoadedWorlds()) {
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
            PortalStructure structure,
            PortalStructureTags tags,
            DungeonPortalStore store,
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
                Optional<PortalStructure.Frame> fromPdc = tags.frameFromKeystone(block);
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
            PortalStructure structure,
            PortalStructureTags tags,
            DungeonPortalStore store,
            PortalStructure.Frame frame) {
        int cx = frame.keystone().getX() >> 4;
        int cz = frame.keystone().getZ() >> 4;
            YapSched.regionChunk(plugin, frame.world(), cx, cz, () -> {
            boolean already = DungeonPortalRegistry.contains(frame);
            if (!already) {
                DungeonPortalRegistry.register(frame);
            }
            store.upsert(frame);
            UUID owner = new UUID(0L, 0L);
            try {
                Optional<String> raw = tags.owner(frame.keystone());
                if (raw.isPresent()) {
                    owner = UUID.fromString(raw.get());
                }
            } catch (Exception ignored) {
            }
            tags.installKeystone(frame, owner);
            // Always re-arm walkability + lime swirl (restarts / OOM drop ItemDisplays)
            structure.ensureWalkable(frame);
        });
    }
}

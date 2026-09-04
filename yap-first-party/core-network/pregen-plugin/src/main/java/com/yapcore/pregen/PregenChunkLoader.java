package com.yapcore.pregen;

import com.yapcore.pregen.shape.ChunkPos;
import com.yapcore.sched.YapSched;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.BiConsumer;

/**
 * Folia-safe chunk generation helper: affinity to owning region + plugin chunk tickets.
 */
public final class PregenChunkLoader {

    private PregenChunkLoader() {
    }

    /**
     * Load/generate {@code (cx, cz)} on the owning region thread, holding a plugin ticket
     * until the async load completes (success or failure).
     */
    public static void load(JavaPlugin plugin, World world, ChunkPos pos,
                            Runnable onSuccess, BiConsumer<ChunkPos, Throwable> onFail) {
        YapSched.regionChunk(plugin, world, pos.x(), pos.z(), () -> {
            try {
                world.addPluginChunkTicket(pos.x(), pos.z(), plugin);
            } catch (Throwable t) {
                // Older Paper may lack tickets — still attempt load.
            }
            try {
                world.getChunkAtAsync(pos.x(), pos.z(), true, chunk ->
                        YapSched.regionChunk(plugin, world, pos.x(), pos.z(), () -> {
                            releaseTicket(plugin, world, pos);
                            onSuccess.run();
                        }));
            } catch (Throwable t) {
                releaseTicket(plugin, world, pos);
                onFail.accept(pos, t);
            }
        });
    }

    private static void releaseTicket(JavaPlugin plugin, World world, ChunkPos pos) {
        try {
            world.removePluginChunkTicket(pos.x(), pos.z(), plugin);
        } catch (Throwable ignored) {
        }
    }

    /** Folia/Paper region bucket key for per-region inflight caps (~8×8 chunks). */
    public static long regionKey(int chunkX, int chunkZ) {
        int rx = chunkX >> 3;
        int rz = chunkZ >> 3;
        return (((long) rx) << 32) ^ (rz & 0xffffffffL);
    }
}

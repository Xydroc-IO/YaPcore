package com.yapcore.world.edit;

import com.yapcore.sched.YapSched;
import com.yapcore.world.CuboidSelection;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Folia-safe lighting refresh after edits (no CFI / NMS section injection).
 * Relies on chunk reload + client block updates via {@code world.refreshChunk}.
 */
public final class LightingService {

    private final JavaPlugin plugin;

    public LightingService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Integer> fixSelection(Player player, CuboidSelection sel) {
        World world = Bukkit.getWorld(sel.world());
        if (world == null) {
            return CompletableFuture.completedFuture(0);
        }
        return fixBounds(player, world, sel.minX(), sel.minY(), sel.minZ(),
                sel.maxX(), sel.maxY(), sel.maxZ());
    }

    public CompletableFuture<Integer> fixBounds(Player player, World world,
                                                int minX, int minY, int minZ,
                                                int maxX, int maxY, int maxZ) {
        Set<Long> chunks = new HashSet<>();
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                chunks.add((((long) x) << 32) ^ (z & 0xffffffffL));
            }
        }
        AtomicInteger refreshed = new AtomicInteger();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (long key : chunks) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            chain = chain.thenCompose(v -> refreshChunk(world, cx, cz, refreshed));
        }
        return chain.thenApply(v -> refreshed.get());
    }

    public CompletableFuture<Integer> fixLastOrSelection(Player player, CuboidSelection selOrNull,
                                                         PlayerEditState.EditBounds last) {
        if (selOrNull != null) {
            return fixSelection(player, selOrNull);
        }
        if (last != null) {
            World world = Bukkit.getWorld(last.world());
            if (world == null) {
                return CompletableFuture.completedFuture(0);
            }
            return fixBounds(player, world, last.minX(), last.minY(), last.minZ(),
                    last.maxX(), last.maxY(), last.maxZ());
        }
        Location loc = player.getLocation();
        return fixBounds(player, loc.getWorld(),
                loc.getBlockX() - 8, loc.getBlockY() - 8, loc.getBlockZ() - 8,
                loc.getBlockX() + 8, loc.getBlockY() + 8, loc.getBlockZ() + 8);
    }

    private CompletableFuture<Void> refreshChunk(World world, int cx, int cz, AtomicInteger counter) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        int bx = cx << 4;
        int bz = cz << 4;
        YapSched.region(plugin, new Location(world, bx, world.getMinHeight(), bz), () -> {
            try {
                Chunk chunk = world.getChunkAt(cx, cz);
                if (!chunk.isLoaded()) {
                    chunk.load(true);
                }
                // Nudge lighting: re-send chunk to nearby players / refresh light engine path
                world.refreshChunk(cx, cz);
                // Touch a no-op on corners so clients recompute adjacent light
                int minY = world.getMinHeight();
                int maxY = Math.min(world.getMaxHeight() - 1, minY + 1);
                world.getBlockAt(bx, minY, bz).getBlockData();
                world.getBlockAt(bx + 15, maxY, bz + 15).getBlockData();
                counter.incrementAndGet();
            } finally {
                done.complete(null);
            }
        });
        return done;
    }
}

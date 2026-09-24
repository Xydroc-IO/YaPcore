package com.yapcore.yapblock.level;

import com.yapcore.sched.YapSched;
import com.yapcore.yapblock.IslandSnapshot;
import com.yapcore.yapblock.db.IslandRepository;
import com.yapcore.yapblock.grid.IslandGrid;
import com.yapcore.yapblock.grid.IslandIndex;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/** Scans island bounds and sums block values into island level. */
public final class IslandLevelScanner {

    private final JavaPlugin plugin;
    private final IslandGrid grid;
    private final IslandIndex index;
    private final IslandRepository islands;
    private final BlockValueTable values;
    private final IslandTopCache topCache;

    public IslandLevelScanner(
            JavaPlugin plugin,
            IslandGrid grid,
            IslandIndex index,
            IslandRepository islands,
            BlockValueTable values,
            IslandTopCache topCache) {
        this.plugin = plugin;
        this.grid = grid;
        this.index = index;
        this.islands = islands;
        this.values = values;
        this.topCache = topCache;
    }

    public CompletableFuture<Long> scan(IslandSnapshot island, World world) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        int cx = grid.worldX(island.gridX());
        int cz = grid.worldZ(island.gridZ());
        int r = island.sizeRadius();
        YapSched.region(plugin, world, cx, cz, () -> {
            try {
                long total = 0L;
                int minY = world.getMinHeight();
                int maxY = world.getMaxHeight();
                for (int x = cx - r; x <= cx + r; x++) {
                    for (int z = cz - r; z <= cz + r; z++) {
                        double dx = x - cx;
                        double dz = z - cz;
                        if ((dx * dx + dz * dz) > (double) r * r) {
                            continue;
                        }
                        for (int y = minY; y < maxY; y++) {
                            Material mat = world.getBlockAt(x, y, z).getType();
                            if (mat.isAir()) {
                                continue;
                            }
                            total += values.valueOf(mat);
                        }
                    }
                }
                final long level = total;
                YapSched.async(plugin, () -> {
                    try {
                        islands.updateLevel(island.id(), level);
                        IslandSnapshot updated = island.withLevel(level);
                        index.put(updated);
                        topCache.invalidate();
                        future.complete(level);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to persist island level", e);
                        future.completeExceptionally(e);
                    }
                });
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}

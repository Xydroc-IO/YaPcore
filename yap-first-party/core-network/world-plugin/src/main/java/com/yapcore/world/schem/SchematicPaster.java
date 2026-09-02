package com.yapcore.world.schem;

import com.yapcore.sched.YapSched;
import com.yapcore.world.util.BlockCodec;
import com.yapcore.world.util.TileCodec;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class SchematicPaster {

    private final JavaPlugin plugin;

    public SchematicPaster(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Integer> paste(Schematic schematic, World targetWorld, int originX, int originY, int originZ) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        YapSched.async(plugin, () -> {
            AtomicInteger placed = new AtomicInteger();
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (Schematic.BlockEntry entry : schematic.blocks()) {
                chain = chain.thenCompose(v -> pasteOne(
                        targetWorld,
                        originX + entry.dx(),
                        originY + entry.dy(),
                        originZ + entry.dz(),
                        entry.encoded(),
                        entry.tileNbt(),
                        placed));
            }
            chain.whenComplete((v, err) -> YapSched.region(plugin,
                    new Location(targetWorld, originX, originY, originZ), () -> {
                        SchematicIO.spawnEntities(schematic, targetWorld, originX, originY, originZ);
                        future.complete(placed.get());
                    }));
        });
        return future;
    }

    private CompletableFuture<Void> pasteOne(World world, int x, int y, int z, String encoded,
                                             String tile, AtomicInteger placed) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Location loc = new Location(world, x, y, z);
        YapSched.region(plugin, loc, () -> {
            Block block = loc.getBlock();
            BlockCodec.apply(block, encoded);
            TileCodec.apply(block, tile);
            placed.incrementAndGet();
            done.complete(null);
        });
        return done;
    }
}

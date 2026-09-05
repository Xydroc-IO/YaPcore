package com.yapcore.world.schem;

import com.yapcore.sched.YapSched;
import com.yapcore.world.edit.BlockBatch;
import com.yapcore.world.edit.MaskEngine;
import com.yapcore.world.edit.PlayerEditState;
import com.yapcore.world.edit.UndoService;
import com.yapcore.world.util.BlockCodec;
import com.yapcore.world.util.TileCodec;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schematic paste — CFI-lite routes through {@link BlockBatch} (parallel chunk waves,
 * preload, progress, undo, large-paste knobs). Legacy per-block path kept behind config.
 */
public final class SchematicPaster {

    private final JavaPlugin plugin;
    private volatile BlockBatch batch;
    private volatile boolean useBlockBatch = true;
    private volatile MaskEngine masks;
    private volatile PlayerEditState editState;

    public SchematicPaster(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public SchematicPaster(JavaPlugin plugin, UndoService undo) {
        this.plugin = plugin;
        this.batch = new BlockBatch(plugin, undo);
    }

    /** Rebind undo/batch after config reload (keeps listener references stable). */
    public void bind(UndoService undo) {
        this.batch = new BlockBatch(plugin, undo);
        if (editState != null) {
            batch.setEditState(editState);
        }
    }

    public void setUseBlockBatch(boolean useBlockBatch) {
        this.useBlockBatch = useBlockBatch;
    }

    public void setMasks(MaskEngine masks) {
        this.masks = masks;
    }

    public void setEditState(PlayerEditState state) {
        this.editState = state;
        if (batch != null) {
            batch.setEditState(state);
        }
    }

    public void setParallelChunks(int n) {
        if (batch != null) {
            batch.setParallelChunks(n);
        }
    }

    public void setLargePasteTuning(int largeBlocks, int parallelLarge, boolean autoFast) {
        if (batch != null) {
            batch.setLargePasteBlocks(largeBlocks);
            batch.setParallelChunksLarge(parallelLarge);
            batch.setAutoFastLarge(autoFast);
        }
    }

    public void setProgressListener(BlockBatch.ProgressListener listener) {
        if (batch != null) {
            batch.setProgressListener(listener);
        }
    }

    public BlockBatch batch() {
        return batch;
    }

    public boolean isLargePaste(int blocks) {
        return batch != null && batch.isLarge(blocks);
    }

    public void cancel(UUID playerId) {
        if (batch != null) {
            batch.requestCancel(playerId);
        }
    }

    /** Paste without player context (no undo / progress). Prefer {@link #paste(Player, Schematic, World, int, int, int)}. */
    public CompletableFuture<Integer> paste(Schematic schematic, World targetWorld,
                                            int originX, int originY, int originZ) {
        return paste(null, schematic, targetWorld, originX, originY, originZ, false);
    }

    public CompletableFuture<Integer> paste(Player player, Schematic schematic, World targetWorld,
                                            int originX, int originY, int originZ) {
        return paste(player, schematic, targetWorld, originX, originY, originZ, false);
    }

    public CompletableFuture<Integer> paste(Player player, Schematic schematic, World targetWorld,
                                            int originX, int originY, int originZ, boolean ignoreAir) {
        if (schematic == null || targetWorld == null) {
            return CompletableFuture.completedFuture(0);
        }
        if (useBlockBatch && batch != null) {
            return pasteBatched(player, schematic, targetWorld, originX, originY, originZ, ignoreAir);
        }
        return pasteLegacy(schematic, targetWorld, originX, originY, originZ);
    }

    private CompletableFuture<Integer> pasteBatched(Player player, Schematic schematic, World world,
                                                    int originX, int originY, int originZ,
                                                    boolean ignoreAir) {
        UUID id = player != null ? player.getUniqueId() : null;
        SchematicPastePlanner.Options opts = new SchematicPastePlanner.Options(
                ignoreAir, true, masks, id);
        List<BlockBatch.Encoded> plans = SchematicPastePlanner.plan(
                schematic, originX, originY, originZ, world, opts);
        return batch.applyEncoded(player, world, plans).thenCompose(n -> {
            if (editState != null && player != null && !schematic.blocks().isEmpty()) {
                Schematic.Bounds b = schematic.bounds();
                editState.setLastEditBounds(player.getUniqueId(), world.getName(),
                        originX, originY, originZ,
                        originX + b.sizeX() - 1,
                        originY + b.sizeY() - 1,
                        originZ + b.sizeZ() - 1);
            }
            CompletableFuture<Void> entities = new CompletableFuture<>();
            YapSched.region(plugin, new Location(world, originX, originY, originZ), () -> {
                try {
                    SchematicIO.spawnEntities(schematic, world, originX, originY, originZ);
                } finally {
                    entities.complete(null);
                }
            });
            return entities.thenApply(v -> n);
        });
    }

    private CompletableFuture<Integer> pasteLegacy(Schematic schematic, World targetWorld,
                                                   int originX, int originY, int originZ) {
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
                        if (err != null) {
                            future.completeExceptionally(err);
                        } else {
                            future.complete(placed.get());
                        }
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

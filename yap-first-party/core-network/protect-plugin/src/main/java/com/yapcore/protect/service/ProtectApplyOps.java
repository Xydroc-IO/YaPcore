package com.yapcore.protect.service;

import com.yapcore.protect.model.ChangeType;
import com.yapcore.protect.model.ProtectChange;
import com.yapcore.protect.util.BlockCodec;
import com.yapcore.protect.util.InventoryCodec;
import com.yapcore.sched.YapSched;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-world apply paths for protect rollback / restore.
 * Block changes are chunk-batched (Folia-safe parallel waves) so WE paste rollbacks
 * finish in seconds instead of hanging on one region hop per block.
 */
final class ProtectApplyOps {

    private static final int WAVE_SIZE = 12;

    private final JavaPlugin plugin;

    ProtectApplyOps(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    Comparator<ProtectChange> rollbackOrder() {
        return (a, b) -> {
            boolean invA = a.changeType() == ChangeType.CONTAINER_INVENTORY;
            boolean invB = b.changeType() == ChangeType.CONTAINER_INVENTORY;
            if (invA && invB) {
                return Long.compare(b.epochMs(), a.epochMs());
            }
            if (invA != invB) {
                return invA ? -1 : 1;
            }
            return Long.compare(a.epochMs(), b.epochMs());
        };
    }

    Comparator<ProtectChange> restoreOrder() {
        return rollbackOrder().reversed();
    }

    /** Apply rollback for many changes; returns successfully applied ids. */
    CompletableFuture<List<Long>> applyRollbackBatch(List<ProtectChange> changes) {
        return applyBatch(changes, true);
    }

    /** Apply restore for many changes; returns successfully applied ids. */
    CompletableFuture<List<Long>> applyRestoreBatch(List<ProtectChange> changes) {
        return applyBatch(changes, false);
    }

    private CompletableFuture<List<Long>> applyBatch(List<ProtectChange> changes, boolean rollback) {
        if (changes == null || changes.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<ProtectChange> inventories = new ArrayList<>();
        List<ProtectChange> blocks = new ArrayList<>();
        for (ProtectChange change : changes) {
            if (change == null) {
                continue;
            }
            if (rollback ? change.rolledBack() : !change.rolledBack()) {
                continue;
            }
            if (!ProtectApplyRules.canApplyWorldState(change.changeType())) {
                continue;
            }
            if (change.changeType() == ChangeType.CONTAINER_INVENTORY) {
                inventories.add(change);
            } else {
                blocks.add(change);
            }
        }

        ConcurrentLinkedQueue<Long> applied = new ConcurrentLinkedQueue<>();
        CompletableFuture<Void> invDone = applyInventoriesSequential(inventories, rollback, applied);
        return invDone.thenCompose(v -> applyBlocksBatched(blocks, rollback, applied))
                .thenApply(v -> List.copyOf(applied));
    }

    private CompletableFuture<Void> applyInventoriesSequential(List<ProtectChange> inventories,
                                                               boolean rollback,
                                                               ConcurrentLinkedQueue<Long> applied) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (ProtectChange change : inventories) {
            chain = chain.thenCompose(v -> applyInventoryAsync(change, rollback).thenAccept(ok -> {
                if (ok) {
                    applied.add(change.id());
                }
            }));
        }
        return chain;
    }

    private CompletableFuture<Void> applyBlocksBatched(List<ProtectChange> blocks, boolean rollback,
                                                       ConcurrentLinkedQueue<Long> applied) {
        if (blocks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Map<String, Map<Long, List<ProtectChange>>> byWorldChunk = new HashMap<>();
        for (ProtectChange change : blocks) {
            if (Bukkit.getWorld(change.world()) == null) {
                continue;
            }
            long key = chunkKey(change.x(), change.z());
            byWorldChunk
                    .computeIfAbsent(change.world(), w -> new HashMap<>())
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .add(change);
        }
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Map.Entry<String, Map<Long, List<ProtectChange>>> worldEntry : byWorldChunk.entrySet()) {
            World world = Bukkit.getWorld(worldEntry.getKey());
            if (world == null) {
                continue;
            }
            List<Map.Entry<Long, List<ProtectChange>>> chunks =
                    new ArrayList<>(worldEntry.getValue().entrySet());
            chunks.sort(Comparator
                    .<Map.Entry<Long, List<ProtectChange>>>comparingLong(e -> e.getKey() & 0xffffffffL)
                    .thenComparingLong(e -> e.getKey() >>> 32));
            chain = chain.thenCompose(v -> applyChunkWaves(world, chunks, rollback, applied, 0));
        }
        return chain;
    }

    private CompletableFuture<Void> applyChunkWaves(World world,
                                                    List<Map.Entry<Long, List<ProtectChange>>> chunks,
                                                    boolean rollback,
                                                    ConcurrentLinkedQueue<Long> applied,
                                                    int offset) {
        if (offset >= chunks.size()) {
            return CompletableFuture.completedFuture(null);
        }
        int end = Math.min(offset + WAVE_SIZE, chunks.size());
        List<CompletableFuture<Void>> wave = new ArrayList<>(end - offset);
        for (int i = offset; i < end; i++) {
            List<ProtectChange> chunkChanges = chunks.get(i).getValue();
            ProtectChange first = chunkChanges.get(0);
            Location anchor = new Location(world, first.x(), first.y(), first.z());
            wave.add(applyChunk(anchor, chunkChanges, rollback, applied));
        }
        return CompletableFuture.allOf(wave.toArray(CompletableFuture[]::new))
                .thenCompose(v -> applyChunkWaves(world, chunks, rollback, applied, end));
    }

    private CompletableFuture<Void> applyChunk(Location anchor, List<ProtectChange> chunkChanges,
                                               boolean rollback, ConcurrentLinkedQueue<Long> applied) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        YapSched.region(plugin, anchor, () -> {
            try {
                for (ProtectChange change : chunkChanges) {
                    String encoded = rollback ? change.blockBefore() : change.blockAfter();
                    try {
                        Block block = anchor.getWorld().getBlockAt(change.x(), change.y(), change.z());
                        BlockCodec.apply(block, encoded);
                        applied.add(change.id());
                    } catch (Exception ignored) {
                        // skip bad row
                    }
                }
            } finally {
                done.complete(null);
            }
        });
        return done.orTimeout(30, TimeUnit.SECONDS).exceptionally(ex -> null);
    }

    private CompletableFuture<Boolean> applyInventoryAsync(ProtectChange change, boolean rollback) {
        String encoded = rollback ? change.blockBefore() : change.blockAfter();
        World world = Bukkit.getWorld(change.world());
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        Location loc = new Location(world, change.x(), change.y(), change.z());
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        YapSched.region(plugin, loc, () -> {
            try {
                BlockState state = loc.getBlock().getState();
                if (!(state instanceof Container container)) {
                    done.complete(false);
                    return;
                }
                Inventory inventory = container.getInventory();
                InventoryCodec.apply(inventory, encoded);
                state.update(true, false);
                done.complete(true);
            } catch (Exception e) {
                done.complete(false);
            }
        });
        return done.orTimeout(10, TimeUnit.SECONDS).exceptionally(ex -> false);
    }

    /** Single-change sync path kept for tests / tiny ops. Prefer batch APIs. */
    boolean applyChangeRollback(ProtectChange change) {
        try {
            return applyRollbackBatch(List.of(change)).get(15, TimeUnit.SECONDS).contains(change.id());
        } catch (Exception e) {
            return false;
        }
    }

    boolean applyChangeRestore(ProtectChange change) {
        try {
            return applyRestoreBatch(List.of(change)).get(15, TimeUnit.SECONDS).contains(change.id());
        } catch (Exception e) {
            return false;
        }
    }

    private static long chunkKey(int x, int z) {
        return (((long) (x >> 4)) << 32) | ((z >> 4) & 0xffffffffL);
    }
}

package com.yapcore.protect.service;

import com.yapcore.protect.BlockChangeRecord;
import com.yapcore.protect.ProtectConfig;
import com.yapcore.protect.ProtectService;
import com.yapcore.protect.db.ChangeRepository;
import com.yapcore.protect.db.ProtectDatabase;
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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ProtectServiceImpl implements ProtectService {

    private final JavaPlugin plugin;
    private ProtectConfig config;
    private ProtectDatabase database;
    private ChangeRepository repository;

    public ProtectServiceImpl(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ProtectConfig config() {
        return config;
    }

    public void start(ProtectConfig config) throws SQLException {
        this.config = config;
        if (database == null) {
            database = new ProtectDatabase(plugin, config);
            database.open();
            repository = new ChangeRepository(database);
        }
        schedulePrune();
    }

    public void reload(ProtectConfig config) {
        this.config = config;
        schedulePrune();
    }

    public void shutdown() {
        if (database != null) {
            database.close();
        }
    }

    public ChangeRepository repository() {
        return repository;
    }

    public void logAsync(ChangeType type, UUID actorUuid, String actorName,
                         String world, int x, int y, int z, String before, String after) {
        if (!isLogging() || repository == null) {
            return;
        }
        YapSched.async(plugin, () -> {
            try {
                repository.insert(config.serverId(), type, actorUuid, actorName,
                        world, x, y, z, before, after);
            } catch (SQLException e) {
                plugin.getLogger().warning("Protect log failed: " + e.getMessage());
            }
        });
    }

    @Override
    public boolean isLogging() {
        return config != null && config.loggingEnabled() && database != null && database.isOpen();
    }

    @Override
    public CompletableFuture<List<BlockChangeRecord>> lookupActor(UUID actorUuid, long fromEpochMs,
                                                                    long toEpochMs, int limit) {
        return CompletableFuture.supplyAsync(() -> query(() ->
                repository.lookupActor(actorUuid, fromEpochMs, toEpochMs, cap(limit))));
    }

    @Override
    public CompletableFuture<List<BlockChangeRecord>> lookupBlock(String world, int x, int y, int z,
                                                                  long fromEpochMs, long toEpochMs, int limit) {
        return CompletableFuture.supplyAsync(() -> query(() ->
                repository.lookupBlock(world, x, y, z, fromEpochMs, toEpochMs, cap(limit))));
    }

    @Override
    public CompletableFuture<List<BlockChangeRecord>> lookupRadius(String world, int cx, int cy, int cz,
                                                                    int radiusBlocks, long fromEpochMs,
                                                                    long toEpochMs, int limit) {
        return CompletableFuture.supplyAsync(() -> query(() ->
                repository.lookupRadius(world, cx, cy, cz,
                        Math.min(radiusBlocks, config.maxRollbackRadius()),
                        fromEpochMs, toEpochMs, cap(limit))));
    }

    @Override
    public CompletableFuture<List<BlockChangeRecord>> lookupTimeRange(String world, long fromEpochMs,
                                                                      long toEpochMs, int limit) {
        return CompletableFuture.supplyAsync(() -> query(() ->
                repository.lookupTimeRange(world, fromEpochMs, toEpochMs, cap(limit))));
    }

    @Override
    public CompletableFuture<Integer> rollbackChanges(List<Long> changeIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<ProtectChange> changes = new ArrayList<>(repository.fetchByIds(changeIds));
                changes.sort(rollbackOrder());
                int applied = 0;
                List<Long> rolled = new ArrayList<>();
                for (ProtectChange change : changes) {
                    if (change.rolledBack()) {
                        continue;
                    }
                    if (applyChangeRollback(change)) {
                        applied++;
                        rolled.add(change.id());
                    }
                }
                if (!rolled.isEmpty()) {
                    repository.markRolledBack(rolled);
                }
                return applied;
            } catch (SQLException e) {
                plugin.getLogger().warning("rollback failed: " + e.getMessage());
                return 0;
            }
        });
    }

    public CompletableFuture<Integer> rollbackRadius(String world, int cx, int cy, int cz,
                                                     int radius, long fromMs, long toMs) {
        return lookupRadius(world, cx, cy, cz, radius, fromMs, toMs, config.maxLookupLimit())
                .thenCompose(rows -> rollbackChanges(rows.stream().map(BlockChangeRecord::id).toList()));
    }

    public CompletableFuture<Integer> rollbackTimeRange(String world, long fromMs, long toMs) {
        return lookupTimeRange(world, fromMs, toMs, config.maxLookupLimit())
                .thenCompose(rows -> rollbackChanges(rows.stream().map(BlockChangeRecord::id).toList()));
    }

    public CompletableFuture<Integer> rollbackUser(UUID actor, long fromMs, long toMs) {
        return lookupActor(actor, fromMs, toMs, config.maxLookupLimit())
                .thenCompose(rows -> rollbackChanges(rows.stream().map(BlockChangeRecord::id).toList()));
    }

    public CompletableFuture<Integer> restoreChanges(List<Long> changeIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<ProtectChange> changes = new ArrayList<>(repository.fetchByIds(changeIds));
                changes.sort(restoreOrder());
                int applied = 0;
                List<Long> restored = new ArrayList<>();
                for (ProtectChange change : changes) {
                    if (!change.rolledBack()) {
                        continue;
                    }
                    if (applyChangeRestore(change)) {
                        applied++;
                        restored.add(change.id());
                    }
                }
                if (!restored.isEmpty()) {
                    repository.clearRolledBack(restored);
                }
                return applied;
            } catch (SQLException e) {
                plugin.getLogger().warning("restore failed: " + e.getMessage());
                return 0;
            }
        });
    }

    public CompletableFuture<Integer> restoreUser(UUID actor, long fromMs, long toMs) {
        return lookupActor(actor, fromMs, toMs, config.maxLookupLimit())
                .thenCompose(rows -> restoreChanges(rows.stream().map(BlockChangeRecord::id).toList()));
    }

    public CompletableFuture<Integer> restoreTimeRange(String world, long fromMs, long toMs) {
        return lookupTimeRange(world, fromMs, toMs, config.maxLookupLimit())
                .thenCompose(rows -> restoreChanges(rows.stream().map(BlockChangeRecord::id).toList()));
    }

    private Comparator<ProtectChange> rollbackOrder() {
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

    private Comparator<ProtectChange> restoreOrder() {
        return rollbackOrder().reversed();
    }

    private boolean applyChangeRollback(ProtectChange change) {
        return switch (change.changeType()) {
            case BLOCK_BREAK, BLOCK_PLACE -> applyBlockState(change, change.blockBefore());
            case CONTAINER_INVENTORY -> applyInventoryState(change, change.blockBefore());
            default -> false;
        };
    }

    private boolean applyChangeRestore(ProtectChange change) {
        return switch (change.changeType()) {
            case BLOCK_BREAK, BLOCK_PLACE -> applyBlockState(change, change.blockAfter());
            case CONTAINER_INVENTORY -> applyInventoryState(change, change.blockAfter());
            default -> false;
        };
    }

    private boolean applyBlockState(ProtectChange change, String encoded) {
        World world = Bukkit.getWorld(change.world());
        if (world == null) {
            return false;
        }
        Location loc = new Location(world, change.x(), change.y(), change.z());
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        YapSched.region(plugin, loc, () -> {
            try {
                Block block = loc.getBlock();
                BlockCodec.apply(block, encoded);
                done.complete(true);
            } catch (Exception e) {
                done.complete(false);
            }
        });
        try {
            return done.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean applyInventoryState(ProtectChange change, String encoded) {
        World world = Bukkit.getWorld(change.world());
        if (world == null) {
            return false;
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
        try {
            return done.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public CompletableFuture<Long> pruneBefore(long epochMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.pruneBefore(epochMs);
            } catch (SQLException e) {
                plugin.getLogger().warning("prune failed: " + e.getMessage());
                return 0L;
            }
        });
    }

    public CompletableFuture<Long> countAll() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.countAll();
            } catch (SQLException e) {
                return 0L;
            }
        });
    }

    private List<BlockChangeRecord> query(QueryFn fn) {
        try {
            return fn.run().stream().map(this::toRecord).toList();
        } catch (SQLException e) {
            plugin.getLogger().warning("protect query failed: " + e.getMessage());
            return List.of();
        }
    }

    private int cap(int limit) {
        return Math.min(limit, config == null ? 200 : config.maxLookupLimit());
    }

    private BlockChangeRecord toRecord(ProtectChange change) {
        return new BlockChangeRecord(
                change.id(),
                change.actorUuid(),
                change.actorName(),
                change.world(),
                change.x(),
                change.y(),
                change.z(),
                change.changeType().name(),
                summarizePayload(change),
                summarizePayloadAfter(change),
                change.epochMs());
    }

    private static String summarizePayload(ProtectChange change) {
        if (change.changeType() == ChangeType.CONTAINER_INVENTORY) {
            return change.blockBefore().isBlank() ? "(empty)" : "(inventory snapshot)";
        }
        return change.blockBefore();
    }

    private static String summarizePayloadAfter(ProtectChange change) {
        if (change.changeType() == ChangeType.CONTAINER_INVENTORY) {
            return change.blockAfter().isBlank() ? "(empty)" : "(inventory snapshot)";
        }
        return change.blockAfter();
    }

    private void schedulePrune() {
        if (config == null || config.pruneDays() <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - config.pruneDays() * 86_400_000L;
        pruneBefore(cutoff);
    }

    @FunctionalInterface
    private interface QueryFn {
        List<ProtectChange> run() throws SQLException;
    }
}

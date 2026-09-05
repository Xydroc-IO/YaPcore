package com.yapcore.protect.service;

import com.yapcore.protect.BlockChangeRecord;
import com.yapcore.protect.ProtectConfig;
import com.yapcore.protect.ProtectLookupCursor;
import com.yapcore.protect.ProtectLookupPage;
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
        repository.setServerId(config.serverId());
        schedulePrune();
    }

    public void reload(ProtectConfig config) {
        this.config = config;
        if (repository != null) {
            repository.setServerId(config.serverId());
        }
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
                repository.lookupActor(actorUuid, fromEpochMs, toEpochMs, pageSize(limit))));
    }

    @Override
    public CompletableFuture<List<BlockChangeRecord>> lookupBlock(String world, int x, int y, int z,
                                                                  long fromEpochMs, long toEpochMs, int limit) {
        return CompletableFuture.supplyAsync(() -> query(() ->
                repository.lookupBlock(world, x, y, z, fromEpochMs, toEpochMs, pageSize(limit))));
    }

    @Override
    public CompletableFuture<List<BlockChangeRecord>> lookupRadius(String world, int cx, int cy, int cz,
                                                                    int radiusBlocks, long fromEpochMs,
                                                                    long toEpochMs, int limit) {
        return CompletableFuture.supplyAsync(() -> query(() ->
                repository.lookupRadius(world, cx, cy, cz,
                        Math.min(radiusBlocks, config.maxRollbackRadius()),
                        fromEpochMs, toEpochMs, pageSize(limit))));
    }

    @Override
    public CompletableFuture<List<BlockChangeRecord>> lookupTimeRange(String world, long fromEpochMs,
                                                                      long toEpochMs, int limit) {
        return CompletableFuture.supplyAsync(() -> query(() ->
                repository.lookupTimeRange(world, fromEpochMs, toEpochMs, pageSize(limit))));
    }

    @Override
    public CompletableFuture<ProtectLookupPage> lookupActorPage(UUID actorUuid, long fromEpochMs,
                                                                long toEpochMs, int pageSize,
                                                                ProtectLookupCursor after) {
        return CompletableFuture.supplyAsync(() -> pageQuery(pageSize, () ->
                repository.lookupActorPage(actorUuid, fromEpochMs, toEpochMs, pageSize(pageSize), after)));
    }

    @Override
    public CompletableFuture<ProtectLookupPage> lookupBlockPage(String world, int x, int y, int z,
                                                                long fromEpochMs, long toEpochMs,
                                                                int pageSize, ProtectLookupCursor after) {
        return CompletableFuture.supplyAsync(() -> pageQuery(pageSize, () ->
                repository.lookupBlockPage(world, x, y, z, fromEpochMs, toEpochMs, pageSize(pageSize), after)));
    }

    @Override
    public CompletableFuture<ProtectLookupPage> lookupRadiusPage(String world, int cx, int cy, int cz,
                                                                 int radiusBlocks, long fromEpochMs,
                                                                 long toEpochMs, int pageSize,
                                                                 ProtectLookupCursor after) {
        return CompletableFuture.supplyAsync(() -> pageQuery(pageSize, () ->
                repository.lookupRadiusPage(world, cx, cy, cz,
                        Math.min(radiusBlocks, config.maxRollbackRadius()),
                        fromEpochMs, toEpochMs, pageSize(pageSize), after)));
    }

    @Override
    public CompletableFuture<ProtectLookupPage> lookupTimeRangePage(String world, long fromEpochMs,
                                                                    long toEpochMs, int pageSize,
                                                                    ProtectLookupCursor after) {
        return CompletableFuture.supplyAsync(() -> pageQuery(pageSize, () ->
                repository.lookupTimeRangePage(world, fromEpochMs, toEpochMs, pageSize(pageSize), after)));
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
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<ProtectChange> all = repository.lookupRadiusAll(world, cx, cy, cz,
                        Math.min(radius, config.maxRollbackRadius()), fromMs, toMs);
                return rollbackChanges(all.stream().map(ProtectChange::id).toList()).join();
            } catch (SQLException e) {
                plugin.getLogger().warning("rollbackRadius failed: " + e.getMessage());
                return 0;
            }
        });
    }

    public CompletableFuture<Integer> rollbackTimeRange(String world, long fromMs, long toMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<ProtectChange> all = repository.lookupTimeRangeAll(world, fromMs, toMs);
                return rollbackChanges(all.stream().map(ProtectChange::id).toList()).join();
            } catch (SQLException e) {
                plugin.getLogger().warning("rollbackTimeRange failed: " + e.getMessage());
                return 0;
            }
        });
    }

    public CompletableFuture<Integer> rollbackUser(UUID actor, long fromMs, long toMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<ProtectChange> all = repository.lookupActorAll(actor, fromMs, toMs);
                return rollbackChanges(all.stream().map(ProtectChange::id).toList()).join();
            } catch (SQLException e) {
                plugin.getLogger().warning("rollbackUser failed: " + e.getMessage());
                return 0;
            }
        });
    }

    @Override
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
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<ProtectChange> all = repository.lookupActorAll(actor, fromMs, toMs);
                return restoreChanges(all.stream().map(ProtectChange::id).toList()).join();
            } catch (SQLException e) {
                plugin.getLogger().warning("restoreUser failed: " + e.getMessage());
                return 0;
            }
        });
    }

    public CompletableFuture<Integer> restoreTimeRange(String world, long fromMs, long toMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<ProtectChange> all = repository.lookupTimeRangeAll(world, fromMs, toMs);
                return restoreChanges(all.stream().map(ProtectChange::id).toList()).join();
            } catch (SQLException e) {
                plugin.getLogger().warning("restoreTimeRange failed: " + e.getMessage());
                return 0;
            }
        });
    }

    /** Full actor window for export (batched pages via repository). */
    public CompletableFuture<List<ProtectChange>> exportActor(UUID actor, long fromMs, long toMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.lookupActorAll(actor, fromMs, toMs);
            } catch (SQLException e) {
                plugin.getLogger().warning("exportActor failed: " + e.getMessage());
                return List.of();
            }
        });
    }

    /** Full time-range window for export (batched pages via repository). */
    public CompletableFuture<List<ProtectChange>> exportTimeRange(String world, long fromMs, long toMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.lookupTimeRangeAll(world, fromMs, toMs);
            } catch (SQLException e) {
                plugin.getLogger().warning("exportTimeRange failed: " + e.getMessage());
                return List.of();
            }
        });
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
        if (!ProtectApplyRules.canRollback(change.changeType())) {
            return false;
        }
        return switch (change.changeType()) {
            case BLOCK_BREAK, BLOCK_PLACE, EXPLOSION, LIQUID_FLOW, FIRE ->
                    applyBlockState(change, change.blockBefore());
            case CONTAINER_INVENTORY -> applyInventoryState(change, change.blockBefore());
            default -> false;
        };
    }

    private boolean applyChangeRestore(ProtectChange change) {
        if (!ProtectApplyRules.canRestore(change.changeType())) {
            return false;
        }
        return switch (change.changeType()) {
            case BLOCK_BREAK, BLOCK_PLACE, EXPLOSION, LIQUID_FLOW, FIRE ->
                    applyBlockState(change, change.blockAfter());
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

    private ProtectLookupPage pageQuery(int pageSize, QueryFn fn) {
        try {
            List<BlockChangeRecord> fetched = fn.run().stream().map(this::toRecord).toList();
            return ProtectLookupPage.of(fetched, pageSize(pageSize));
        } catch (SQLException e) {
            plugin.getLogger().warning("protect page query failed: " + e.getMessage());
            return new ProtectLookupPage(List.of(), null, false);
        }
    }

    /** Page size for lookups — config value is the default page, not a hard total cap. */
    private int pageSize(int limit) {
        int max = config == null ? 50 : config.maxLookupLimit();
        if (limit <= 0) {
            return max;
        }
        return Math.min(limit, Math.max(max, 200));
    }

    private BlockChangeRecord toRecord(ProtectChange change) {
        return new BlockChangeRecord(
                change.id(),
                change.serverId(),
                change.actorUuid(),
                change.actorName(),
                change.world(),
                change.x(),
                change.y(),
                change.z(),
                change.changeType().name(),
                summarizePayload(change),
                summarizePayloadAfter(change),
                change.epochMs(),
                change.rolledBack());
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

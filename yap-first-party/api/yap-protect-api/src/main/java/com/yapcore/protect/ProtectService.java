package com.yapcore.protect;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * CoreProtect-class audit + rollback contract.
 * Provided by {@code YaPProtect} via {@code ServicesManager}.
 */
public interface ProtectService {

    boolean isLogging();

    CompletableFuture<List<BlockChangeRecord>> lookupActor(UUID actorUuid, long fromEpochMs,
                                                           long toEpochMs, int limit);

    CompletableFuture<List<BlockChangeRecord>> lookupBlock(String world, int x, int y, int z,
                                                           long fromEpochMs, long toEpochMs, int limit);

    CompletableFuture<List<BlockChangeRecord>> lookupRadius(String world, int cx, int cy, int cz,
                                                              int radiusBlocks, long fromEpochMs,
                                                              long toEpochMs, int limit);

    CompletableFuture<List<BlockChangeRecord>> lookupTimeRange(String world, long fromEpochMs,
                                                               long toEpochMs, int limit);

    CompletableFuture<Integer> rollbackChanges(List<Long> changeIds);

    CompletableFuture<Long> pruneBefore(long epochMs);
}

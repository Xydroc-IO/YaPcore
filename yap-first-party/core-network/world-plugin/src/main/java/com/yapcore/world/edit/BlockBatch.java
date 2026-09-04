package com.yapcore.world.edit;

import com.yapcore.sched.YapSched;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Chunk-batched block writes (Folia-safe) with undo recording and large-paste parallelism.
 * Large pastes: preload chunks, higher wave size, optional skip-undo, percent progress.
 */
public final class BlockBatch {

    public record Planned(int x, int y, int z, Material material, BlockData data) {
        public Planned(int x, int y, int z, Material material) {
            this(x, y, z, material, null);
        }
    }

    public record Encoded(int x, int y, int z, String encoded, String tileNbt) {
        public Encoded(int x, int y, int z, String encoded) {
            this(x, y, z, encoded, null);
        }
    }

    /** Progress: player, blocksSoFar, totalBlocksEstimate, chunksDone, chunksTotal. */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(UUID playerId, int blocksSoFar, int totalBlocks, int chunksDone, int chunksTotal);
    }

    private final JavaPlugin plugin;
    private final UndoService undo;
    private volatile int parallelChunks = 4;
    private volatile int parallelChunksLarge = 12;
    private volatile int largePasteBlocks = 50_000;
    private volatile boolean autoFastLarge = true;
    private volatile PlayerEditState editState;
    private volatile BiConsumer<UUID, Integer> progressHook;
    private volatile ProgressListener progressListener;

    public BlockBatch(JavaPlugin plugin, UndoService undo) {
        this.plugin = plugin;
        this.undo = undo;
    }

    public void setParallelChunks(int n) {
        this.parallelChunks = Math.max(1, Math.min(32, n));
    }

    public void setParallelChunksLarge(int n) {
        this.parallelChunksLarge = Math.max(1, Math.min(48, n));
    }

    public void setLargePasteBlocks(int n) {
        this.largePasteBlocks = Math.max(1_000, n);
    }

    public void setAutoFastLarge(boolean autoFastLarge) {
        this.autoFastLarge = autoFastLarge;
    }

    public void setEditState(PlayerEditState editState) {
        this.editState = editState;
    }

    public void setProgressHook(BiConsumer<UUID, Integer> progressHook) {
        this.progressHook = progressHook;
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public boolean isLarge(int blockCount) {
        return blockCount >= largePasteBlocks;
    }

    public CompletableFuture<Integer> apply(Player player, World world, List<Planned> planned) {
        if (planned.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        Map<Long, List<Planned>> byChunk = new HashMap<>();
        for (Planned p : planned) {
            long key = chunkKey(p.x(), p.z());
            byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        boolean large = isLarge(planned.size());
        boolean skipUndo = shouldSkipUndo(player, large);
        EditSession session = skipUndo ? null : new EditSession();
        AtomicInteger changed = new AtomicInteger();
        List<Map.Entry<Long, List<Planned>>> chunks = sortedChunkEntries(byChunk);
        int wave = waveSize(large);
        int totalBlocks = planned.size();
        int totalChunks = chunks.size();

        AtomicInteger chunksDone = new AtomicInteger();
        return applyPlannedParallel(world, chunks, session, changed, 0, wave,
                player.getUniqueId(), totalBlocks, totalChunks, chunksDone)
                .thenApply(v -> {
                    if (session != null) {
                        undo.push(player.getUniqueId(), session);
                    }
                    notifyDone(player.getUniqueId(), changed.get());
                    return changed.get();
                });
    }

    public CompletableFuture<Integer> applyEncoded(Player player, World world, List<Encoded> planned) {
        if (planned.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        Map<Long, List<Encoded>> byChunk = new HashMap<>();
        for (Encoded p : planned) {
            long key = chunkKey(p.x(), p.z());
            byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        boolean large = isLarge(planned.size());
        boolean skipUndo = shouldSkipUndo(player, large);
        EditSession session = skipUndo ? null : new EditSession();
        AtomicInteger changed = new AtomicInteger();
        List<Map.Entry<Long, List<Encoded>>> chunks = sortedChunkEntries(byChunk);
        int wave = waveSize(large);
        int totalBlocks = planned.size();
        int totalChunks = chunks.size();

        AtomicInteger chunksDone = new AtomicInteger();
        return applyEncodedParallel(world, chunks, session, changed, 0, wave,
                player.getUniqueId(), totalBlocks, totalChunks, chunksDone, skipUndo)
                .thenApply(v -> {
                    if (session != null) {
                        undo.push(player.getUniqueId(), session);
                    }
                    notifyDone(player.getUniqueId(), changed.get());
                    return changed.get();
                });
    }

    private boolean shouldSkipUndo(Player player, boolean large) {
        if (editState != null && editState.isFast(player.getUniqueId())) {
            return true;
        }
        return large && autoFastLarge;
    }

    private int waveSize(boolean large) {
        return large ? parallelChunksLarge : parallelChunks;
    }

    private void notifyDone(UUID id, int changed) {
        if (progressHook != null && changed > 0) {
            progressHook.accept(id, changed);
        }
    }

    private CompletableFuture<Void> preloadChunkKeys(World world, List<long[]> chunkCoords) {
        if (chunkCoords.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Void>> wave = new ArrayList<>();
        for (long[] c : chunkCoords) {
            int cx = (int) c[0];
            int cz = (int) c[1];
            Location anchor = new Location(world, (cx << 4) + 8, world.getMinHeight(), (cz << 4) + 8);
            CompletableFuture<Void> done = new CompletableFuture<>();
            YapSched.region(plugin, anchor, () -> {
                try {
                    Chunk chunk = world.getChunkAt(cx, cz);
                    if (!chunk.isLoaded()) {
                        chunk.load(true);
                    }
                } finally {
                    done.complete(null);
                }
            });
            wave.add(done);
        }
        return CompletableFuture.allOf(wave.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> applyPlannedParallel(World world,
                                                         List<Map.Entry<Long, List<Planned>>> chunks,
                                                         EditSession session, AtomicInteger changed,
                                                         int offset, int waveSize,
                                                         UUID playerId, int totalBlocks, int totalChunks,
                                                         AtomicInteger chunksDone) {
        if (offset >= chunks.size()) {
            return CompletableFuture.completedFuture(null);
        }
        int end = Math.min(offset + waveSize, chunks.size());
        List<long[]> preload = new ArrayList<>();
        for (int i = offset; i < end; i++) {
            Planned first = chunks.get(i).getValue().get(0);
            preload.add(new long[]{first.x() >> 4, first.z() >> 4});
        }
        return preloadChunkKeys(world, preload).thenCompose(v -> {
            List<CompletableFuture<Void>> wave = new ArrayList<>();
            for (int i = offset; i < end; i++) {
                List<Planned> chunkPlans = chunks.get(i).getValue();
                Planned first = chunkPlans.get(0);
                Location anchor = new Location(world, first.x(), first.y(), first.z());
                wave.add(applyChunk(session, world, anchor, chunkPlans, changed));
            }
            return CompletableFuture.allOf(wave.toArray(CompletableFuture[]::new));
        }).thenCompose(v -> {
            int done = chunksDone.addAndGet(end - offset);
            if (progressListener != null) {
                progressListener.onProgress(playerId, changed.get(), totalBlocks, done, totalChunks);
            } else if (progressHook != null && totalChunks >= 8
                    && (done % Math.max(1, totalChunks / 10) == 0 || done == totalChunks)) {
                progressHook.accept(playerId, changed.get());
            }
            return applyPlannedParallel(world, chunks, session, changed, end, waveSize,
                    playerId, totalBlocks, totalChunks, chunksDone);
        });
    }

    private CompletableFuture<Void> applyEncodedParallel(World world,
                                                         List<Map.Entry<Long, List<Encoded>>> chunks,
                                                         EditSession session, AtomicInteger changed,
                                                         int offset, int waveSize,
                                                         UUID playerId, int totalBlocks, int totalChunks,
                                                         AtomicInteger chunksDone, boolean skipUndo) {
        if (offset >= chunks.size()) {
            return CompletableFuture.completedFuture(null);
        }
        int end = Math.min(offset + waveSize, chunks.size());
        List<long[]> preload = new ArrayList<>();
        for (int i = offset; i < end; i++) {
            Encoded first = chunks.get(i).getValue().get(0);
            preload.add(new long[]{first.x() >> 4, first.z() >> 4});
        }
        return preloadChunkKeys(world, preload).thenCompose(v -> {
            List<CompletableFuture<Void>> wave = new ArrayList<>();
            for (int i = offset; i < end; i++) {
                List<Encoded> chunkPlans = chunks.get(i).getValue();
                Encoded first = chunkPlans.get(0);
                Location anchor = new Location(world, first.x(), first.y(), first.z());
                wave.add(applyEncodedChunk(session, world, anchor, chunkPlans, changed, skipUndo));
            }
            return CompletableFuture.allOf(wave.toArray(CompletableFuture[]::new));
        }).thenCompose(v -> {
            int done = chunksDone.addAndGet(end - offset);
            if (progressListener != null) {
                progressListener.onProgress(playerId, changed.get(), totalBlocks, done, totalChunks);
            } else if (progressHook != null && totalChunks >= 8
                    && (done % Math.max(1, totalChunks / 10) == 0 || done == totalChunks)) {
                progressHook.accept(playerId, changed.get());
            }
            return applyEncodedParallel(world, chunks, session, changed, end, waveSize,
                    playerId, totalBlocks, totalChunks, chunksDone, skipUndo);
        });
    }

    private CompletableFuture<Void> applyChunk(EditSession session, World world, Location anchor,
                                               List<Planned> plans, AtomicInteger changed) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        YapSched.region(plugin, anchor, () -> {
            try {
                for (Planned p : plans) {
                    Block block = world.getBlockAt(p.x(), p.y(), p.z());
                    if (session == null) {
                        if (p.data() != null) {
                            block.setBlockData(p.data(), false);
                        } else {
                            block.setType(p.material(), false);
                        }
                        changed.incrementAndGet();
                        continue;
                    }
                    String before = BlockCodec.encode(block);
                    if (p.data() != null) {
                        block.setBlockData(p.data(), false);
                    } else {
                        block.setType(p.material(), false);
                    }
                    String after = BlockCodec.encode(block);
                    if (!before.equals(after)) {
                        session.record(world.getName(), p.x(), p.y(), p.z(), before, after);
                        changed.incrementAndGet();
                    }
                }
            } finally {
                done.complete(null);
            }
        });
        return done;
    }

    private CompletableFuture<Void> applyEncodedChunk(EditSession session, World world, Location anchor,
                                                      List<Encoded> plans, AtomicInteger changed,
                                                      boolean skipUndo) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        YapSched.region(plugin, anchor, () -> {
            try {
                for (Encoded p : plans) {
                    Block block = world.getBlockAt(p.x(), p.y(), p.z());
                    if (skipUndo || session == null) {
                        BlockCodec.apply(block, p.encoded());
                        if (p.tileNbt() != null) {
                            com.yapcore.world.util.TileCodec.apply(block, p.tileNbt());
                        }
                        changed.incrementAndGet();
                        continue;
                    }
                    String before = BlockCodec.encode(block);
                    BlockCodec.apply(block, p.encoded());
                    if (p.tileNbt() != null) {
                        com.yapcore.world.util.TileCodec.apply(block, p.tileNbt());
                    }
                    String after = BlockCodec.encode(block);
                    if (!before.equals(after) || p.tileNbt() != null) {
                        session.record(world.getName(), p.x(), p.y(), p.z(), before, after);
                        changed.incrementAndGet();
                    }
                }
            } finally {
                done.complete(null);
            }
        });
        return done;
    }

    private static long chunkKey(int x, int z) {
        return (((long) (x >> 4)) << 32) | ((z >> 4) & 0xffffffffL);
    }

    private static <T> List<Map.Entry<Long, List<T>>> sortedChunkEntries(Map<Long, List<T>> byChunk) {
        List<Map.Entry<Long, List<T>>> list = new ArrayList<>(byChunk.entrySet());
        // Spatial order: by chunk Z then X — better Folia region locality for adjacent waves
        list.sort(Comparator.comparingLong((Map.Entry<Long, List<T>> e) -> e.getKey() & 0xffffffffL)
                .thenComparingLong(e -> e.getKey() >>> 32));
        return list;
    }

    /** Parse {@code stone} or {@code 50%stone,50%dirt}. */
    public static Material pickPattern(String pattern) {
        return PatternEngine.pickMaterial(pattern);
    }

    public static List<Weighted> parsePattern(String pattern) {
        List<Weighted> out = new ArrayList<>();
        if (pattern == null || pattern.isBlank()) {
            return out;
        }
        for (String part : pattern.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int weight = 100;
            String name = token;
            int pct = token.indexOf('%');
            if (pct > 0) {
                try {
                    weight = Integer.parseInt(token.substring(0, pct).trim());
                    name = token.substring(pct + 1).trim();
                } catch (NumberFormatException ignored) {
                    weight = 100;
                }
            }
            Material mat = Material.matchMaterial(name);
            if (mat != null && mat.isBlock()) {
                out.add(new Weighted(Math.max(1, weight), mat));
            }
        }
        return out;
    }

    public record Weighted(int weight, Material material) {
    }

    public static void forEachBlock(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                    TriConsumer consumer) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    consumer.accept(x, y, z);
                }
            }
        }
    }

    @FunctionalInterface
    public interface TriConsumer {
        void accept(int x, int y, int z);
    }
}

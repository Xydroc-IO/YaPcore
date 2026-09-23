package com.yapcore.world.edit;

import com.yapcore.world.util.BlockCodec;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player undo/redo stacks. Apply uses the same chunk-parallel {@link BlockBatch}
 * path as paste so undoing a large schem is not one Folia region hop per block.
 */
public final class UndoService {

    private final int maxSessions;
    private final BlockBatch batch;
    private final Map<UUID, Deque<EditSession>> undoStacks = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<EditSession>> redoStacks = new ConcurrentHashMap<>();

    public UndoService(JavaPlugin plugin, int maxSessions) {
        this.maxSessions = Math.max(1, maxSessions);
        // Shared apply path only — undo of undo is not recorded (player=null → no history push).
        this.batch = new BlockBatch(plugin, this);
    }

    public void setParallelChunks(int n) {
        batch.setParallelChunks(n);
    }

    public void setLargePasteTuning(int largeBlocks, int parallelLarge) {
        batch.setLargePasteBlocks(largeBlocks);
        batch.setParallelChunksLarge(parallelLarge);
        // Never skip history while applying undo/redo payloads.
        batch.setAutoFastLarge(false);
    }

    public void push(UUID playerId, EditSession session) {
        if (session == null || session.isEmpty()) {
            return;
        }
        Deque<EditSession> undo = undoStacks.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        undo.push(session);
        while (undo.size() > maxSessions) {
            undo.removeLast();
        }
        redoStacks.remove(playerId);
    }

    public CompletableFuture<Integer> undo(UUID playerId) {
        Deque<EditSession> undo = undoStacks.get(playerId);
        if (undo == null || undo.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        EditSession session = undo.pop();
        return applySession(session, true).thenApply(count -> {
            redoStacks.computeIfAbsent(playerId, id -> new ArrayDeque<>()).push(session);
            return count;
        });
    }

    public CompletableFuture<Integer> redo(UUID playerId) {
        Deque<EditSession> redo = redoStacks.get(playerId);
        if (redo == null || redo.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        EditSession session = redo.pop();
        return applySession(session, false).thenApply(count -> {
            undoStacks.computeIfAbsent(playerId, id -> new ArrayDeque<>()).push(session);
            return count;
        });
    }

    public int undoDepth(UUID playerId) {
        Deque<EditSession> undo = undoStacks.get(playerId);
        return undo == null ? 0 : undo.size();
    }

    public int redoDepth(UUID playerId) {
        Deque<EditSession> redo = redoStacks.get(playerId);
        return redo == null ? 0 : redo.size();
    }

    public void clearHistory(UUID playerId) {
        undoStacks.remove(playerId);
        redoStacks.remove(playerId);
    }

    private CompletableFuture<Integer> applySession(EditSession session, boolean undo) {
        Map<String, List<BlockBatch.Encoded>> byWorld = new LinkedHashMap<>();
        for (EditSession.BlockEdit edit : session.edits()) {
            if (Bukkit.getWorld(edit.world()) == null) {
                continue;
            }
            String target = undo ? edit.before() : edit.after();
            if (target == null || target.isBlank()) {
                continue;
            }
            byWorld.computeIfAbsent(edit.world(), w -> new ArrayList<>())
                    .add(new BlockBatch.Encoded(edit.x(), edit.y(), edit.z(), target));
        }
        if (byWorld.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (Map.Entry<String, List<BlockBatch.Encoded>> entry : byWorld.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            List<BlockBatch.Encoded> plans = entry.getValue();
            chain = chain.thenCompose(sum ->
                    batch.applyEncoded(null, world, plans).thenApply(n -> sum + n));
        }
        return chain;
    }
}

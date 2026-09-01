package com.yapcore.world.edit;

import com.yapcore.sched.YapSched;
import com.yapcore.world.util.BlockCodec;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class UndoService {

    private final JavaPlugin plugin;
    private final int maxSessions;
    private final Map<UUID, Deque<EditSession>> undoStacks = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<EditSession>> redoStacks = new ConcurrentHashMap<>();

    public UndoService(JavaPlugin plugin, int maxSessions) {
        this.plugin = plugin;
        this.maxSessions = Math.max(1, maxSessions);
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

    private CompletableFuture<Integer> applySession(EditSession session, boolean undo) {
        CompletableFuture<Integer> result = CompletableFuture.completedFuture(0);
        for (EditSession.BlockEdit edit : session.edits()) {
            Location loc = EditSession.location(edit);
            if (loc == null) {
                continue;
            }
            String target = undo ? edit.before() : edit.after();
            result = result.thenCompose(count -> applyOne(loc, target).thenApply(ok -> ok ? count + 1 : count));
        }
        return result;
    }

    private CompletableFuture<Boolean> applyOne(Location loc, String encoded) {
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        YapSched.region(plugin, loc, () -> {
            try {
                BlockCodec.apply(loc.getBlock(), encoded);
                done.complete(true);
            } catch (Exception e) {
                done.complete(false);
            }
        });
        return done;
    }
}

package com.yapcore.disasters;

import com.yapcore.sched.YapTask;
import org.bukkit.Location;

import java.util.List;

/** One active disaster FX session for a world. */
final class DisasterActive {
    final DisasterType type;
    final long endsAtMs;
    final Location anchor;
    YapTask task;
    YapTask endTask;
    private final List<YapTask> undos = new java.util.concurrent.CopyOnWriteArrayList<>();

    DisasterActive(DisasterType type, long endsAtMs, Location anchor) {
        this.type = type;
        this.endsAtMs = endsAtMs;
        this.anchor = anchor;
    }

    int undoCount() {
        return undos.size();
    }

    void addUndo(YapTask undo) {
        if (undo != null) {
            undos.add(undo);
        }
    }

    void removeUndo(YapTask undo) {
        if (undo != null) {
            undos.remove(undo);
        }
    }

    void cancelUndos() {
        for (YapTask undo : undos) {
            if (undo != null) {
                undo.cancel();
            }
        }
        undos.clear();
    }
}

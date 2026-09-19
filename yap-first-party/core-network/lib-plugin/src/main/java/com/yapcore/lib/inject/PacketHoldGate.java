package com.yapcore.lib.inject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-direction packet sequencer. Later packets wait while one is held for a
 * region decision so order stays intact. All methods run on the connection
 * event loop.
 */
public final class PacketHoldGate<T> {

    private final ArrayDeque<T> queued = new ArrayDeque<>();
    private boolean held;

    public boolean acceptNow() {
        return !held && queued.isEmpty();
    }

    public void enqueue(T item) {
        queued.addLast(item);
    }

    public void hold() {
        held = true;
    }

    public void release() {
        held = false;
    }

    public boolean held() {
        return held;
    }

    public T poll() {
        if (held) {
            return null;
        }
        return queued.pollFirst();
    }

    public List<T> snapshotAndClear() {
        List<T> out = new ArrayList<>(queued);
        queued.clear();
        held = false;
        return out;
    }
}

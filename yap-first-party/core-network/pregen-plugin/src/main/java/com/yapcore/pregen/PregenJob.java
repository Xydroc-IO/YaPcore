package com.yapcore.pregen;

import com.yapcore.pregen.shape.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class PregenJob {

    public enum State { RUNNING, PAUSED, DONE, CANCELLED }

    private final String worldName;
    private final String shapeDescription;
    private final long total;
    private final Deque<ChunkPos> remaining;
    private final AtomicInteger done = new AtomicInteger();
    private final AtomicInteger inflight = new AtomicInteger();
    private volatile State state = State.RUNNING;
    private final long startedAtMs = System.currentTimeMillis();
    private volatile long lastBroadcastMs;

    public PregenJob(String worldName, String shapeDescription, List<ChunkPos> coords) {
        this(worldName, shapeDescription, coords, 0, coords.size());
    }

    public PregenJob(String worldName, String shapeDescription, List<ChunkPos> coords,
                     int alreadyDone, long originalTotal) {
        this.worldName = worldName;
        this.shapeDescription = shapeDescription;
        this.total = Math.max(originalTotal, coords.size() + alreadyDone);
        this.remaining = new ArrayDeque<>(coords);
        this.done.set(Math.max(0, alreadyDone));
    }

    public String worldName() {
        return worldName;
    }

    public String shapeDescription() {
        return shapeDescription;
    }

    public long total() {
        return total;
    }

    public int done() {
        return done.get();
    }

    public int remainingCount() {
        return remaining.size();
    }

    public int inflight() {
        return inflight.get();
    }

    public State state() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public long startedAtMs() {
        return startedAtMs;
    }

    public long lastBroadcastMs() {
        return lastBroadcastMs;
    }

    public void markBroadcast() {
        lastBroadcastMs = System.currentTimeMillis();
    }

    public synchronized ChunkPos poll() {
        return remaining.pollFirst();
    }

    public synchronized void requeue(ChunkPos pos) {
        remaining.addFirst(pos);
    }

    public synchronized void requeueBack(ChunkPos pos) {
        remaining.addLast(pos);
    }

    public void beginInflight() {
        inflight.incrementAndGet();
    }

    public void endInflightSuccess() {
        inflight.decrementAndGet();
        done.incrementAndGet();
    }

    public void endInflightFail(ChunkPos pos) {
        inflight.decrementAndGet();
        requeue(pos);
    }

    public synchronized List<ChunkPos> snapshotRemaining() {
        return new ArrayList<>(remaining);
    }

    public synchronized boolean isQueueEmpty() {
        return remaining.isEmpty() && inflight.get() == 0;
    }

    public double progressPercent() {
        if (total <= 0) {
            return 100.0;
        }
        return 100.0 * done.get() / total;
    }

    public double ratePerSecond() {
        long elapsed = Math.max(1L, System.currentTimeMillis() - startedAtMs);
        return done.get() * 1000.0 / elapsed;
    }

    public String statusLine() {
        return worldName + " [" + state + "] " + done.get() + "/" + total
                + String.format(" (%.1f%%)", progressPercent())
                + " rem=" + remaining.size()
                + " in-flight=" + inflight.get()
                + " " + String.format("%.1f ch/s", ratePerSecond())
                + " — " + shapeDescription;
    }
}

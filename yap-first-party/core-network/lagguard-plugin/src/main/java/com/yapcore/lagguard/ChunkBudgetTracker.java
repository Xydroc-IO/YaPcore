package com.yapcore.lagguard;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Sliding windows and trip counters for chunk budgets. */
public final class ChunkBudgetTracker {

    private final ConcurrentHashMap<String, Window> hoppers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> redstone = new ConcurrentHashMap<>();
    private final AtomicLong trips = new AtomicLong();
    private final AtomicLong entitiesCancelled = new AtomicLong();
    private final AtomicLong tntCancelled = new AtomicLong();
    private final AtomicLong hopperThrottled = new AtomicLong();
    private final AtomicLong redstoneThrottled = new AtomicLong();

    public static String key(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    public boolean tryHopper(String key, int limit, long windowTicks, long tick) {
        return tryWindow(hoppers, key, limit, windowTicks, tick, hopperThrottled);
    }

    public boolean tryRedstone(String key, int limit, long windowTicks, long tick) {
        return tryWindow(redstone, key, limit, windowTicks, tick, redstoneThrottled);
    }

    public void tripEntity() {
        trips.incrementAndGet();
        entitiesCancelled.incrementAndGet();
    }

    public void tripTnt() {
        trips.incrementAndGet();
        tntCancelled.incrementAndGet();
    }

    public void tripHopper() {
        trips.incrementAndGet();
    }

    public void tripRedstone() {
        trips.incrementAndGet();
    }

    public long trips() {
        return trips.get();
    }

    public long entitiesCancelled() {
        return entitiesCancelled.get();
    }

    public long tntCancelled() {
        return tntCancelled.get();
    }

    public long hopperThrottled() {
        return hopperThrottled.get();
    }

    public long redstoneThrottled() {
        return redstoneThrottled.get();
    }

    private boolean tryWindow(ConcurrentHashMap<String, Window> map, String key, int limit,
                              long windowTicks, long tick, AtomicLong throttleCounter) {
        long bucket = tick / Math.max(1L, windowTicks);
        Window w = map.compute(key, (k, prev) -> {
            if (prev == null || prev.bucket != bucket) {
                return new Window(bucket, 0);
            }
            return prev;
        });
        synchronized (w) {
            if (w.bucket != bucket) {
                w.bucket = bucket;
                w.count = 0;
            }
            w.count++;
            if (w.count > limit) {
                throttleCounter.incrementAndGet();
                return false;
            }
            return true;
        }
    }

    private static final class Window {
        long bucket;
        int count;

        Window(long bucket, int count) {
            this.bucket = bucket;
            this.count = count;
        }
    }
}

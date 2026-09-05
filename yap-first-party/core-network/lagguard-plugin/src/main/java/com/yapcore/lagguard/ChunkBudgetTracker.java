package com.yapcore.lagguard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Sliding windows and trip counters for chunk budgets. */
public final class ChunkBudgetTracker {

    private final ConcurrentHashMap<String, Window> hoppers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> redstone = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> pistons = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> observers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> escalation = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> chunkTrips = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> windowTrips = new ConcurrentHashMap<>();
    private final AtomicLong trips = new AtomicLong();
    private final AtomicLong entitiesCancelled = new AtomicLong();
    private final AtomicLong tntCancelled = new AtomicLong();
    private final AtomicLong hopperThrottled = new AtomicLong();
    private final AtomicLong redstoneThrottled = new AtomicLong();
    private final AtomicLong pistonThrottled = new AtomicLong();
    private final AtomicLong observerThrottled = new AtomicLong();
    private final AtomicLong escalationCulls = new AtomicLong();

    public static String key(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    public boolean tryHopper(String key, int limit, long windowTicks, long tick) {
        return tryWindow(hoppers, key, limit, windowTicks, tick, hopperThrottled);
    }

    public boolean tryRedstone(String key, int limit, long windowTicks, long tick) {
        return tryWindow(redstone, key, limit, windowTicks, tick, redstoneThrottled);
    }

    public boolean tryPiston(String key, int limit, long windowTicks, long tick) {
        return tryWindow(pistons, key, limit, windowTicks, tick, pistonThrottled);
    }

    public boolean tryObserver(String key, int limit, long windowTicks, long tick) {
        return tryWindow(observers, key, limit, windowTicks, tick, observerThrottled);
    }

    /**
     * Returns true once per escalation window when trips in that window meet {@code threshold}.
     * Call only after deciding cull is enabled.
     */
    public boolean tryEscalationCull(String key, int threshold, long windowTicks, long tick) {
        if (threshold <= 0) {
            return false;
        }
        long bucket = tick / Math.max(1L, windowTicks);
        Window w = escalation.compute(key, (k, prev) -> {
            if (prev == null || prev.bucket != bucket) {
                return new Window(bucket, 0);
            }
            return prev;
        });
        synchronized (w) {
            if (w.bucket != bucket) {
                w.bucket = bucket;
                w.count = 0;
                windowTrips.computeIfAbsent(key, k -> new AtomicLong()).set(0);
            }
            long tripCount = windowTrips.computeIfAbsent(key, k -> new AtomicLong()).get();
            if (tripCount < threshold) {
                return false;
            }
            if (w.count > 0) {
                return false; // already culled this window
            }
            w.count = 1;
            return true;
        }
    }

    public void noteTripForEscalation(String key, long windowTicks, long tick) {
        if (key == null || key.isBlank()) {
            return;
        }
        long bucket = tick / Math.max(1L, windowTicks);
        Window w = escalation.compute(key, (k, prev) -> {
            if (prev == null || prev.bucket != bucket) {
                return new Window(bucket, 0);
            }
            return prev;
        });
        synchronized (w) {
            if (w.bucket != bucket) {
                w.bucket = bucket;
                w.count = 0;
                windowTrips.computeIfAbsent(key, k -> new AtomicLong()).set(0);
            }
        }
        windowTrips.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    public long escalationTrips(String key) {
        AtomicLong n = windowTrips.get(key);
        return n == null ? 0L : n.get();
    }

    public void tripEntity(String key) {
        trips.incrementAndGet();
        entitiesCancelled.incrementAndGet();
        recordChunkTrip(key);
    }

    public void tripTnt(String key) {
        trips.incrementAndGet();
        tntCancelled.incrementAndGet();
        recordChunkTrip(key);
    }

    public void tripHopper(String key) {
        trips.incrementAndGet();
        recordChunkTrip(key);
    }

    public void tripRedstone(String key) {
        trips.incrementAndGet();
        recordChunkTrip(key);
    }

    public void tripPiston(String key) {
        trips.incrementAndGet();
        recordChunkTrip(key);
    }

    public void tripObserver(String key) {
        trips.incrementAndGet();
        recordChunkTrip(key);
    }

    public void tripMinecart(String key) {
        trips.incrementAndGet();
        entitiesCancelled.incrementAndGet();
        recordChunkTrip(key);
    }

    public void recordEscalationCull() {
        escalationCulls.incrementAndGet();
    }

    public List<LagGuardService.ChunkTrip> topChunks(int n) {
        int limit = Math.max(1, n);
        List<LagGuardService.ChunkTrip> out = new ArrayList<>();
        for (Map.Entry<String, AtomicLong> e : chunkTrips.entrySet()) {
            String[] parts = e.getKey().split(":", 3);
            if (parts.length != 3) {
                continue;
            }
            try {
                out.add(new LagGuardService.ChunkTrip(
                        parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        e.getValue().get()));
            } catch (NumberFormatException ignored) {
            }
        }
        out.sort(Comparator.comparingLong(LagGuardService.ChunkTrip::trips).reversed());
        if (out.size() > limit) {
            return List.copyOf(out.subList(0, limit));
        }
        return List.copyOf(out);
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

    public long pistonThrottled() {
        return pistonThrottled.get();
    }

    public long observerThrottled() {
        return observerThrottled.get();
    }

    public long escalationCulls() {
        return escalationCulls.get();
    }

    private void recordChunkTrip(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        chunkTrips.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    private boolean tryWindow(ConcurrentHashMap<String, Window> map, String key, int limit,
                              long windowTicks, long tick, AtomicLong throttleCounter) {
        if (limit <= 0) {
            return true; // disabled
        }
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

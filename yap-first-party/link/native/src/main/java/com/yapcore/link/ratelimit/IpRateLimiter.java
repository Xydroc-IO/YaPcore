package com.yapcore.link.ratelimit;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sliding-window per-IP counter. Thread-safe for Netty event loops.
 * Windows are fixed-size buckets keyed by {@code now / windowMs}.
 */
public final class IpRateLimiter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepMs = new AtomicLong();

    public boolean tryAcquire(String ip, int limit, long windowMs) {
        if (ip == null || ip.isBlank() || limit <= 0 || windowMs <= 0L) {
            return true;
        }
        long now = System.currentTimeMillis();
        long bucket = now / windowMs;
        Window w = windows.compute(ip, (k, prev) -> {
            if (prev == null || prev.bucket != bucket) {
                return new Window(bucket, new AtomicInteger(0));
            }
            return prev;
        });
        int n = w.count.incrementAndGet();
        maybeSweep(now, windowMs);
        return n <= limit;
    }

    public int size() {
        return windows.size();
    }

    private void maybeSweep(long now, long windowMs) {
        long prev = lastSweepMs.get();
        if (now - prev < Math.max(5_000L, windowMs * 4)) {
            return;
        }
        if (!lastSweepMs.compareAndSet(prev, now)) {
            return;
        }
        long keepAfter = (now / windowMs) - 2;
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().bucket < keepAfter) {
                it.remove();
            }
        }
    }

    private record Window(long bucket, AtomicInteger count) {
    }
}

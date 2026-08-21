package com.yaplabs.yapengine.sequencing;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Monotonic microsecond-precision clock for interaction sequencing.
 * Uses {@link System#nanoTime()} (not wall clock) so tokens never go backwards
 * across NTP adjustments. Resolution is floored to whole microseconds.
 */
public final class SequenceClock {

    private static final SequenceClock INSTANCE = new SequenceClock();

    /** Anchors nanoTime ↔ display micros for diagnostics. */
    private final long nanoOrigin;
    private final long microsOrigin;
    private final AtomicLong lastIssuedMicros = new AtomicLong();

    private SequenceClock() {
        this.nanoOrigin = System.nanoTime();
        this.microsOrigin = System.currentTimeMillis() * 1_000L;
    }

    public static SequenceClock get() {
        return INSTANCE;
    }

    /**
     * Monotonic microseconds since JVM time-base, never decreases.
     * If two calls land in the same microsecond, the second is bumped +1.
     */
    public long nextMicros() {
        long raw = (System.nanoTime() - nanoOrigin) / 1_000L;
        while (true) {
            long prev = lastIssuedMicros.get();
            long next = Math.max(raw, prev + 1);
            if (lastIssuedMicros.compareAndSet(prev, next)) {
                return next;
            }
        }
    }

    public long nowMicros() {
        return (System.nanoTime() - nanoOrigin) / 1_000L;
    }

    public long microsOriginWallApprox() {
        return microsOrigin;
    }
}

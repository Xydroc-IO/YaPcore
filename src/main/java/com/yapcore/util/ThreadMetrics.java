package com.yapcore.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Lightweight cross-core metrics so logs show which logical thread owns which work.
 */
public final class ThreadMetrics {

    private static final Logger LOG = Logger.getLogger("YaPcore.Metrics");
    private static final ConcurrentHashMap<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();

    private ThreadMetrics() {
    }

    public static void record(String component, String action) {
        bump(component, action);
        LOG.info(() -> "[" + component + "] " + action
                + " | thread=" + Thread.currentThread().getName());
    }

    /** Increment without log noise (watchdog samples, hot counters). */
    public static void bump(String component, String action) {
        String key = component + ":" + action;
        COUNTERS.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    public static long get(String component, String action) {
        AtomicLong counter = COUNTERS.get(component + ":" + action);
        return counter == null ? 0L : counter.get();
    }

    public static void dumpSummary() {
        LOG.info("--- YaPcore metrics snapshot ---");
        COUNTERS.forEach((key, value) ->
                LOG.info(() -> "  " + key + " = " + value.get()));
    }

    /** Immutable snapshot for crash reports. */
    public static java.util.Map<String, Long> snapshot() {
        java.util.LinkedHashMap<String, Long> map = new java.util.LinkedHashMap<>();
        COUNTERS.forEach((key, value) -> map.put(key, value.get()));
        return java.util.Collections.unmodifiableMap(map);
    }

    /** Distinct counter keys — high growth over months = cardinality leak. */
    public static int keyCount() {
        return COUNTERS.size();
    }

    /** Test / harness only. */
    public static void resetForTests() {
        COUNTERS.clear();
    }
}

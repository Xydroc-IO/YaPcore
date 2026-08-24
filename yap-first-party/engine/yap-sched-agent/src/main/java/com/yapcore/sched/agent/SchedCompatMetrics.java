package com.yapcore.sched.agent;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Counters for dashboard / smoke assertions. */
public final class SchedCompatMetrics {

    private static final LongAdder SHIM_FIRES = new LongAdder();
    private static final LongAdder GLOBAL_FALLBACKS = new LongAdder();
    private static final LongAdder ENTITY_ROUTES = new LongAdder();
    private static final LongAdder REGION_ROUTES = new LongAdder();
    private static final AtomicLong LAST_FIRE_MS = new AtomicLong();
    private static volatile boolean enabled = true;

    private SchedCompatMetrics() {
    }

    /** Visible across Paper URLClassLoader injections. */
    public static void configure(SchedCompatOptions opts) {
        enabled = opts.metrics();
    }

    public static void recordShim(String route) {
        if (!enabled) {
            return;
        }
        SHIM_FIRES.increment();
        LAST_FIRE_MS.set(System.currentTimeMillis());
        switch (route) {
            case "entity" -> ENTITY_ROUTES.increment();
            case "region" -> REGION_ROUTES.increment();
            default -> GLOBAL_FALLBACKS.increment();
        }
    }

    public static long shimFires() {
        return SHIM_FIRES.sum();
    }

    public static long globalFallbacks() {
        return GLOBAL_FALLBACKS.sum();
    }

    public static long entityRoutes() {
        return ENTITY_ROUTES.sum();
    }

    public static long regionRoutes() {
        return REGION_ROUTES.sum();
    }

    public static long lastFireMs() {
        return LAST_FIRE_MS.get();
    }

    /** Reset counters (tests / smoke). */
    public static void reset() {
        SHIM_FIRES.reset();
        GLOBAL_FALLBACKS.reset();
        ENTITY_ROUTES.reset();
        REGION_ROUTES.reset();
        LAST_FIRE_MS.set(0);
    }
}

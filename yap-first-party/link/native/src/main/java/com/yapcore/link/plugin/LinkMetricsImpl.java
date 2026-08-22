package com.yapcore.link.plugin;

import com.yapcore.link.api.LinkMetrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory metrics for dashboard hooks. */
public final class LinkMetricsImpl implements LinkMetrics {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    @Override
    public void counter(String name, long delta) {
        counters.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
    }

    @Override
    public void gauge(String name, long value) {
        gauges.computeIfAbsent(name, k -> new AtomicLong()).set(value);
    }

    @Override
    public long counter(String name) {
        AtomicLong v = counters.get(name);
        return v == null ? 0L : v.get();
    }

    public long gauge(String name) {
        AtomicLong v = gauges.get(name);
        return v == null ? 0L : v.get();
    }
}

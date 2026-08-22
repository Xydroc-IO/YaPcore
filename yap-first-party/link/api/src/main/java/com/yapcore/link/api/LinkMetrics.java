package com.yapcore.link.api;

/** Dashboard / ops metrics surface (Phase 5). */
public interface LinkMetrics {

    void counter(String name, long delta);

    void gauge(String name, long value);

    long counter(String name);
}

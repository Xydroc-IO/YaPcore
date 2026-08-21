package com.yapcore.stacker;

import java.util.concurrent.atomic.AtomicLong;

/** In-process counters (exposed via GUI, command, PlaceholderAPI). */
public final class StackerMetrics {

    private final AtomicLong mobMerges = new AtomicLong();
    private final AtomicLong mobKillsProcessed = new AtomicLong();
    private final AtomicLong itemMerges = new AtomicLong();
    private final AtomicLong spawnerStacks = new AtomicLong();
    private final AtomicLong auraKills = new AtomicLong();

    public void mobMerge() {
        mobMerges.incrementAndGet();
    }

    public void mobKillProcessed() {
        mobKillsProcessed.incrementAndGet();
    }

    public void itemMerge() {
        itemMerges.incrementAndGet();
    }

    public void spawnerStack() {
        spawnerStacks.incrementAndGet();
    }

    public void auraKill() {
        auraKills.incrementAndGet();
    }

    public long mobMerges() {
        return mobMerges.get();
    }

    public long mobKillsProcessed() {
        return mobKillsProcessed.get();
    }

    public long itemMerges() {
        return itemMerges.get();
    }

    public long spawnerStacks() {
        return spawnerStacks.get();
    }

    public long auraKills() {
        return auraKills.get();
    }

    public void reset() {
        mobMerges.set(0);
        mobKillsProcessed.set(0);
        itemMerges.set(0);
        spawnerStacks.set(0);
        auraKills.set(0);
    }
}

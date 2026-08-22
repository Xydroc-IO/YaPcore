package com.yapcore.combat.status;

import java.util.UUID;

public final class ActiveStatusEffect {

    private final String effectId;
    private int stacks;
    private long expiresAtMs;
    private long nextTickAtMs;
    private final UUID sourceId;

    public ActiveStatusEffect(String effectId, int stacks, long expiresAtMs, long nextTickAtMs, UUID sourceId) {
        this.effectId = effectId;
        this.stacks = stacks;
        this.expiresAtMs = expiresAtMs;
        this.nextTickAtMs = nextTickAtMs;
        this.sourceId = sourceId;
    }

    public String effectId() {
        return effectId;
    }

    public int stacks() {
        return stacks;
    }

    public void setStacks(int stacks) {
        this.stacks = stacks;
    }

    public long expiresAtMs() {
        return expiresAtMs;
    }

    public void setExpiresAtMs(long expiresAtMs) {
        this.expiresAtMs = expiresAtMs;
    }

    public long nextTickAtMs() {
        return nextTickAtMs;
    }

    public void setNextTickAtMs(long nextTickAtMs) {
        this.nextTickAtMs = nextTickAtMs;
    }

    public UUID sourceId() {
        return sourceId;
    }
}

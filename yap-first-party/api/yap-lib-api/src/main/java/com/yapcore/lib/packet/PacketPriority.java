package com.yapcore.lib.packet;

/**
 * Listener order. Lower values run first. {@link #MONITOR} sees the final
 * cancelled flag and cannot cancel.
 */
public enum PacketPriority {
    LOWEST(0),
    LOW(1),
    NORMAL(2),
    HIGH(3),
    HIGHEST(4),
    MONITOR(5);

    private final int slot;

    PacketPriority(int slot) {
        this.slot = slot;
    }

    public int slot() {
        return slot;
    }

    public boolean canCancel() {
        return this != MONITOR;
    }
}

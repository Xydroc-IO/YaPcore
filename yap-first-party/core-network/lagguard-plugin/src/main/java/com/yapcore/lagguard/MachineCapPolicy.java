package com.yapcore.lagguard;

/**
 * Pure policy for piston / observer / minecart budgets.
 * Limit {@code <= 0} means the specific cap is disabled.
 */
public final class MachineCapPolicy {

    private MachineCapPolicy() {
    }

    /** @return true when the event should be cancelled (count already at/over limit). */
    public static boolean overWindowLimit(int countInWindow, int limit) {
        return limit > 0 && countInWindow >= limit;
    }

    /** @return true when a new minecart spawn should be cancelled. */
    public static boolean overMinecartCap(int minecartsInChunk, int limit) {
        return limit > 0 && minecartsInChunk >= limit;
    }
}

package com.yapcore.lagguard;

/**
 * Pure policy for optional escalation item cull.
 * <p>
 * <b>Danger:</b> when enabled, LagGuard may remove ground {@code Item} entities from a hot
 * chunk once per window after trip counts exceed a threshold. This can destroy player drops.
 * Keep {@code escalation.enabled: false} unless operators accept that risk.
 */
public final class EscalationCullPolicy {

    private EscalationCullPolicy() {
    }

    public static boolean shouldCull(boolean enabled, long tripsInWindow, int threshold) {
        return enabled && threshold > 0 && tripsInWindow >= threshold;
    }

    /**
     * How many ground items to remove to get back under {@code itemCap}.
     * Caps the removal at {@code maxRemove} for safety.
     */
    public static int itemsToRemove(int currentItems, int itemCap, int maxRemove) {
        if (itemCap <= 0 || maxRemove <= 0 || currentItems <= itemCap) {
            return 0;
        }
        int excess = currentItems - itemCap;
        return Math.min(excess, maxRemove);
    }
}

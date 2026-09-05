package com.yapcore.lagguard;

/**
 * Pure limit checks for entity-type caps (per-chunk density).
 * Limit {@code <= 0} means the category cap is disabled.
 */
public final class EntityCapPolicy {

    private EntityCapPolicy() {
    }

    /**
     * @param categoryCount how many entities of this category already exist in the chunk
     * @param limit         configured per-chunk cap ({@code <= 0} = disabled)
     * @return true when the spawn should be cancelled
     */
    public static boolean overLimit(int categoryCount, int limit) {
        return limit > 0 && categoryCount >= limit;
    }

    public static int limitFor(EntityCapCategory category, int items, int mobs, int projectiles) {
        return switch (category) {
            case ITEMS -> items;
            case MOBS -> mobs;
            case PROJECTILES -> projectiles;
            case OTHER -> 0;
        };
    }
}

package com.yapcore.protect.service;

import com.yapcore.protect.model.ChangeType;

/**
 * Pure rules for which protect change types can be rolled back / restored in-world.
 * Block + container inventory are restorable; access/kill are lookup-only.
 */
public final class ProtectApplyRules {

    private ProtectApplyRules() {
    }

    public static boolean canApplyWorldState(ChangeType type) {
        return type == ChangeType.BLOCK_BREAK
                || type == ChangeType.BLOCK_PLACE
                || type == ChangeType.CONTAINER_INVENTORY
                || type == ChangeType.EXPLOSION
                || type == ChangeType.LIQUID_FLOW
                || type == ChangeType.FIRE;
    }

    /** Rollback applies {@code blockBefore} (or inventory before). */
    public static boolean canRollback(ChangeType type) {
        return canApplyWorldState(type);
    }

    /** Restore applies {@code blockAfter} only after a prior rollback. */
    public static boolean canRestore(ChangeType type) {
        return canApplyWorldState(type);
    }

    public static boolean isLookupOnly(ChangeType type) {
        return !canApplyWorldState(type);
    }
}

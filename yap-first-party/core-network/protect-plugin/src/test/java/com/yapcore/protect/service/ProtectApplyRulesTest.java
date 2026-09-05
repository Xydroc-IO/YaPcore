package com.yapcore.protect.service;

import com.yapcore.protect.model.ChangeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Block + chest inventory are restorable; access/kill stay lookup-only. */
class ProtectApplyRulesTest {

    @Test
    void blockAndChestInventoryAreRestorable() {
        assertTrue(ProtectApplyRules.canRollback(ChangeType.BLOCK_BREAK));
        assertTrue(ProtectApplyRules.canRestore(ChangeType.BLOCK_BREAK));
        assertTrue(ProtectApplyRules.canRollback(ChangeType.BLOCK_PLACE));
        assertTrue(ProtectApplyRules.canRestore(ChangeType.BLOCK_PLACE));
        assertTrue(ProtectApplyRules.canRollback(ChangeType.CONTAINER_INVENTORY));
        assertTrue(ProtectApplyRules.canRestore(ChangeType.CONTAINER_INVENTORY));
        assertTrue(ProtectApplyRules.canRollback(ChangeType.EXPLOSION));
        assertTrue(ProtectApplyRules.canRollback(ChangeType.LIQUID_FLOW));
        assertTrue(ProtectApplyRules.canRollback(ChangeType.FIRE));
    }

    @Test
    void accessAndKillAreLookupOnly() {
        assertTrue(ProtectApplyRules.isLookupOnly(ChangeType.CONTAINER_ACCESS));
        assertTrue(ProtectApplyRules.isLookupOnly(ChangeType.ENTITY_KILL));
        assertFalse(ProtectApplyRules.canRollback(ChangeType.CONTAINER_ACCESS));
        assertFalse(ProtectApplyRules.canRestore(ChangeType.ENTITY_KILL));
    }
}

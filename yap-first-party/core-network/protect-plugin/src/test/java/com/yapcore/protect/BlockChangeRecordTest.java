package com.yapcore.protect;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockChangeRecordTest {

    @Test
    void restorableTypes() {
        assertTrue(record("BLOCK_BREAK").restorable());
        assertTrue(record("BLOCK_PLACE").restorable());
        assertTrue(record("CONTAINER_INVENTORY").restorable());
        assertTrue(record("EXPLOSION").restorable());
        assertTrue(record("LIQUID_FLOW").restorable());
        assertTrue(record("FIRE").restorable());
        assertFalse(record("CONTAINER_ACCESS").restorable());
        assertFalse(record("ENTITY_KILL").restorable());
    }

    private static BlockChangeRecord record(String type) {
        return new BlockChangeRecord(1L, "lobby", UUID.randomUUID(), "Steve", "world",
                0, 64, 0, type, "air", "stone", 0L, false);
    }
}

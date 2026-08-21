package com.yapcore.playerdata.sync;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Serialization smoke tests (empty slots — no Material registry required).
 */
class ItemSerializerTest {

    @Test
    void emptyRoundTripPreservesSize() {
        byte[] bytes = ItemSerializer.empty(41);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        ItemStack[] back = ItemSerializer.deserialize(bytes, 41);
        assertEquals(41, back.length);
        for (ItemStack stack : back) {
            assertEquals(null, stack);
        }
    }

    @Test
    void nullDataUsesFallbackSize() {
        ItemStack[] back = ItemSerializer.deserialize(null, 27);
        assertEquals(27, back.length);
    }

    @Test
    void serializeSparseArray() {
        ItemStack[] items = new ItemStack[9];
        byte[] bytes = ItemSerializer.serialize(items);
        ItemStack[] back = ItemSerializer.deserialize(bytes, 9);
        assertEquals(9, back.length);
    }
}

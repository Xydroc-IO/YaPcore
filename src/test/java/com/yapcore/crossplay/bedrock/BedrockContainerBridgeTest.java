package com.yapcore.crossplay.bedrock;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4.6 container open/close. */
class BedrockContainerBridgeTest {

    @Test
    void openChestSendsPackets() {
        BedrockContainerBridge bridge = new BedrockContainerBridge();
        List<ByteBuf> sent = new ArrayList<>();
        bridge.setSender((user, buf) -> sent.add(buf));
        var w = bridge.open("Steve", BedrockContainerBridge.TYPE_CHEST, 10, 64, -3);
        assertEquals(BedrockContainerBridge.TYPE_CHEST, w.type());
        assertEquals(2, sent.size()); // open + content
        for (ByteBuf b : sent) {
            assertNotNull(b);
            b.release();
        }
        bridge.close("Steve", true);
    }

    @Test
    void typeForBlockHints() {
        assertEquals(BedrockContainerBridge.TYPE_FURNACE,
                BedrockContainerBridge.typeForBlock("BLAST_FURNACE"));
        assertEquals(BedrockContainerBridge.TYPE_ENCHANT,
                BedrockContainerBridge.typeForBlock("ENCHANTING_TABLE"));
        assertEquals(BedrockContainerBridge.TYPE_CHEST,
                BedrockContainerBridge.typeForBlock("CHEST"));
        assertEquals(BedrockContainerBridge.TYPE_ANVIL,
                BedrockContainerBridge.typeForBlock("ANVIL"));
        assertEquals(BedrockContainerBridge.TYPE_ANVIL,
                BedrockContainerBridge.typeForBlock("CHIPPED_ANVIL"));
        assertEquals(BedrockContainerBridge.TYPE_SMITHING,
                BedrockContainerBridge.typeForBlock("SMITHING_TABLE"));
        assertEquals(BedrockContainerBridge.TYPE_LOOM,
                BedrockContainerBridge.typeForBlock("LOOM"));
        assertEquals(BedrockContainerBridge.TYPE_STONECUTTER,
                BedrockContainerBridge.typeForBlock("STONECUTTER"));
        assertEquals(BedrockContainerBridge.TYPE_CARTOGRAPHY,
                BedrockContainerBridge.typeForBlock("CARTOGRAPHY_TABLE"));
    }

    @Test
    void specialtySlotCountsAndOpen() {
        BedrockContainerBridge bridge = new BedrockContainerBridge();
        assertEquals(3, bridge.slotsForType(BedrockContainerBridge.TYPE_ANVIL));
        assertEquals(4, bridge.slotsForType(BedrockContainerBridge.TYPE_SMITHING));
        assertEquals(4, bridge.slotsForType(BedrockContainerBridge.TYPE_LOOM));
        assertEquals(2, bridge.slotsForType(BedrockContainerBridge.TYPE_STONECUTTER));
        assertEquals(3, bridge.slotsForType(BedrockContainerBridge.TYPE_CARTOGRAPHY));

        assertTrue(BedrockContainerBridge.isVirtualContainer(BedrockContainerBridge.TYPE_ANVIL));
        assertTrue(BedrockContainerBridge.isVirtualContainer(BedrockContainerBridge.TYPE_SMITHING));
        assertFalse(BedrockContainerBridge.isVirtualContainer(BedrockContainerBridge.TYPE_CHEST));
        assertFalse(BedrockContainerBridge.isVirtualContainer(BedrockContainerBridge.TYPE_FURNACE));

        int[] types = {
                BedrockContainerBridge.TYPE_ANVIL,
                BedrockContainerBridge.TYPE_SMITHING,
                BedrockContainerBridge.TYPE_LOOM,
                BedrockContainerBridge.TYPE_STONECUTTER,
                BedrockContainerBridge.TYPE_CARTOGRAPHY
        };
        for (int type : types) {
            List<ByteBuf> sent = new ArrayList<>();
            bridge.setSender((user, buf) -> sent.add(buf));
            var w = bridge.open("Alex", type, 1, 64, 2);
            assertEquals(type, w.type());
            assertEquals(2, sent.size());
            for (ByteBuf b : sent) {
                b.release();
            }
            sent.clear();
            bridge.close("Alex", true);
        }
    }
}

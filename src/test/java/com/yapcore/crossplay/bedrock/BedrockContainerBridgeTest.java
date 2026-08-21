package com.yapcore.crossplay.bedrock;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    }
}

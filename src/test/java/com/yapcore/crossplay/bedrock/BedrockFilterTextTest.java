package com.yapcore.crossplay.bedrock;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stretch: FILTER_TEXT encode/decode + anvil rename echo path. */
class BedrockFilterTextTest {

    @Test
    void filterTextRoundTrip() {
        ByteBuf pkt = BedrockPacketCodec.filterText("Renamed Blade", false);
        assertNotNull(pkt);
        BedrockPacketCodec.Decoded decoded = BedrockPacketCodec.decode(pkt);
        assertEquals(BedrockPacketIds.FILTER_TEXT.id, decoded.id());
        BedrockPacketCodec.FilterTextDecode d = BedrockPacketCodec.tryDecodeFilterText(decoded.body());
        assertNotNull(d);
        assertEquals("Renamed Blade", d.text());
        assertFalse(d.fromServer());
        pkt.release();
    }

    @Test
    void anvilOpenEchoesServerFilterText() {
        BedrockContainerBridge bridge = new BedrockContainerBridge();
        List<ByteBuf> sent = new ArrayList<>();
        bridge.setSender((user, buf) -> sent.add(buf));
        bridge.open("Steve", BedrockContainerBridge.TYPE_ANVIL, 0, 64, 0);
        sent.clear();

        ByteBuf inbound = BedrockPacketCodec.filterText("Epic", false);
        BedrockPacketCodec.Decoded decoded = BedrockPacketCodec.decode(inbound);
        bridge.handleFilterText("Steve", decoded.body());
        inbound.release();

        assertEquals(1, sent.size());
        BedrockPacketCodec.Decoded echo = BedrockPacketCodec.decode(sent.get(0));
        assertEquals(BedrockPacketIds.FILTER_TEXT.id, echo.id());
        BedrockPacketCodec.FilterTextDecode d = BedrockPacketCodec.tryDecodeFilterText(echo.body());
        assertNotNull(d);
        assertEquals("Epic", d.text());
        assertTrue(d.fromServer());
        sent.get(0).release();
        bridge.close("Steve", true);
    }

    @Test
    void nonAnvilIgnoresPaperButStillEchoes() {
        BedrockContainerBridge bridge = new BedrockContainerBridge();
        AtomicReference<ByteBuf> echo = new AtomicReference<>();
        bridge.setSender((user, buf) -> echo.set(buf));
        bridge.open("Alex", BedrockContainerBridge.TYPE_CHEST, 1, 64, 1);
        echo.set(null);

        ByteBuf inbound = BedrockPacketCodec.filterText("x", false);
        bridge.handleFilterText("Alex", BedrockPacketCodec.decode(inbound).body());
        inbound.release();

        assertNotNull(echo.get());
        echo.get().release();
        bridge.close("Alex", true);
    }
}

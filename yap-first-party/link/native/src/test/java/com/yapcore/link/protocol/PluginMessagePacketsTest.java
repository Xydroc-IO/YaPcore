package com.yapcore.link.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PluginMessagePacketsTest {

    @Test
    void roundTripServerbound766() {
        int protocol = 766;
        byte[] payload = "hello network".getBytes();
        ByteBuf buf = Unpooled.buffer();
        PluginMessagePackets.writeServerbound(buf, protocol, "yap:chat", payload);

        var parsed = PluginMessagePackets.tryParseServerbound(protocol, buf);
        assertTrue(parsed.isPresent());
        assertEquals("yap:chat", parsed.get().channel());
        assertArrayEquals(payload, parsed.get().data());
    }

    @Test
    void roundTripClientbound768() {
        int protocol = 768;
        byte[] payload = new byte[]{1, 2, 3};
        ByteBuf buf = Unpooled.buffer();
        PluginMessagePackets.writeClientbound(buf, protocol, "yap:chat", payload);

        var parsed = PluginMessagePackets.tryParseClientbound(protocol, buf);
        assertTrue(parsed.isPresent());
        assertEquals("yap:chat", parsed.get().channel());
        assertArrayEquals(payload, parsed.get().data());
    }
}

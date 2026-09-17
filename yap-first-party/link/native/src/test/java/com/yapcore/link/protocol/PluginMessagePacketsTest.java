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

    @Test
    void roundTripClientbound776() {
        int protocol = 776;
        assertEquals(0x18, PluginMessagePackets.clientboundPlayId(protocol));
        assertEquals(0x16, PluginMessagePackets.serverboundPlayId(protocol));
        byte[] payload = new byte[]{9, 8, 7};
        ByteBuf buf = Unpooled.buffer();
        PluginMessagePackets.writeClientbound(buf, protocol, "BungeeCord", payload);
        var parsed = PluginMessagePackets.tryParseClientbound(protocol, buf);
        assertTrue(parsed.isPresent());
        assertEquals("BungeeCord", parsed.get().channel());
        assertArrayEquals(payload, parsed.get().data());
    }

    @Test
    void sniffConnectIgnoresWrongPacketId() throws Exception {
        byte[] payload;
        try (var bytes = new java.io.ByteArrayOutputStream();
             var out = new java.io.DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF("survival");
            payload = bytes.toByteArray();
        }
        ByteBuf buf = Unpooled.buffer();
        // Deliberately wrong play id — sniff must still find the target.
        McCodec.writeVarInt(buf, 0x99);
        McCodec.writeString(buf, "bungeecord:main");
        buf.writeBytes(payload);
        var target = PluginMessagePackets.sniffBungeeConnectTarget(buf);
        assertTrue(target.isPresent());
        assertEquals("survival", target.get());
    }
}

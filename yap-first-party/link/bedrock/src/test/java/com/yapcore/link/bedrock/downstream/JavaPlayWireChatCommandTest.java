package com.yapcore.link.bedrock.downstream;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JavaPlayWireChatCommandTest {

    @Test
    void chatCommandIsStringOnlyNoSignedTrailer() {
        ByteBuf buf = JavaPlayWire.chatCommand("/gamemode creative");
        try {
            // id 7 + string "gamemode creative" — no timestamp/salt/signature trailer
            assertTrue(buf.readableBytes() < 40, "bytes=" + buf.readableBytes());
            assertEquals(JavaPlayWire.SB_CHAT_COMMAND, buf.readByte() & 0xFF);
            // Remaining is McCodec string: varint length + utf8
            int len = 0;
            int shift = 0;
            byte b;
            do {
                b = buf.readByte();
                len |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            byte[] utf = new byte[len];
            buf.readBytes(utf);
            assertEquals("gamemode creative", new String(utf, java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(0, buf.readableBytes(), "no trailing signed fields");
        } finally {
            buf.release();
        }
    }
}

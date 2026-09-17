package com.yapcore.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yapcore.link.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

final class ClientSessionCommandsTest {

    @Test
    void extractsUnsignedChatCommand() {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 7);
        McCodec.writeString(buf, "hub");
        assertEquals("hub", ClientSessionCommands.extractCommandLine(770, buf));
        assertEquals(0, buf.readerIndex());
        buf.release();
    }

    @Test
    void extractsServerWithArgs() {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 7);
        McCodec.writeString(buf, "server survival");
        assertEquals("server survival", ClientSessionCommands.extractCommandLine(775, buf));
        buf.release();
    }

    @Test
    void ignoresNonCommandPackets() {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 5);
        McCodec.writeString(buf, "hello");
        assertNull(ClientSessionCommands.extractCommandLine(770, buf));
        buf.release();
    }

    @Test
    void recognizesChatCommandIds() {
        assertTrue(ClientSessionCommands.isChatCommandPacket(770, 7));
        assertTrue(ClientSessionCommands.isChatCommandPacket(770, 8));
        assertFalse(ClientSessionCommands.isChatCommandPacket(770, 5));
    }
}

package com.yapcore.link.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

final class PlayChatTest {

    @Test
    void modernSystemChatUsesNbtStringNotJson() {
        ByteBuf pkt = PlayChat.systemChatPacket(776, PlayChat.jsonText("Hello"), false);
        assertEquals(121, McCodec.readVarInt(pkt));
        assertEquals(8, pkt.readByte()); // TAG_String
        // DataOutput.writeUTF: unsigned short length + bytes
        int utfLen = pkt.readUnsignedShort();
        assertEquals(5, utfLen);
        byte[] bytes = new byte[utfLen];
        pkt.readBytes(bytes);
        assertEquals("Hello", new String(bytes));
        assertTrue(pkt.isReadable());
        assertEquals(0, pkt.readByte()); // overlay=false
        assertEquals(0, pkt.readableBytes());
        pkt.release();
    }

    @Test
    void legacySystemChatStillJsonString() {
        ByteBuf pkt = PlayChat.systemChatPacket(760, PlayChat.jsonText("Hi"), false);
        assertEquals(0x5F, McCodec.readVarInt(pkt));
        String json = McCodec.readString(pkt, 256);
        assertTrue(json.contains("Hi"));
        pkt.release();
    }

    @Test
    void plainTextExtractsJsonTextField() {
        assertEquals("Already connected to lobby",
                PlayChat.plainText(PlayChat.jsonText("Already connected to lobby")));
        assertEquals("raw", PlayChat.plainText("raw"));
    }

    @Test
    void isDisconnectPacketMatchesPlayDisconnectId() {
        ByteBuf pkt = PlayChat.disconnectPacket(776, PlayChat.jsonText("Server closed"));
        assertTrue(PlayChat.isDisconnectPacket(776, pkt));
        assertEquals(0x20, McCodec.readVarInt(pkt));
        pkt.release();
        ByteBuf chat = PlayChat.systemChatPacket(776, PlayChat.jsonText("x"), false);
        org.junit.jupiter.api.Assertions.assertFalse(PlayChat.isDisconnectPacket(776, chat));
        chat.release();
    }
}

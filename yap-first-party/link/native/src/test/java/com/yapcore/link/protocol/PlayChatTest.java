package com.yapcore.link.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlayChatTest {

    @Test
    void systemChatIdMatchesProtocolDumps() {
        assertEquals(121, PlayChat.systemChatId(776));
        assertEquals(121, PlayChat.systemChatId(775));
        assertEquals(119, PlayChat.systemChatId(773));
        assertEquals(115, PlayChat.systemChatId(768));
        assertEquals(108, PlayChat.systemChatId(766));
    }

    @Test
    void playLoginIdMatchesProtocolDumps() {
        assertEquals(49, PlayChat.playLoginId(776));
        assertEquals(48, PlayChat.playLoginId(773));
        assertEquals(43, PlayChat.playLoginId(766));
    }

    @Test
    void advertiseSecureChatFlipsLastBooleanOnLogin() {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 49);
        buf.writeBytes(new byte[]{1, 2, 3, 4, 0});
        PlayChat.advertiseSecureChat(776, buf);
        assertEquals(1, buf.getByte(buf.writerIndex() - 1));
        assertEquals(0, buf.readerIndex());
        buf.release();
    }

    @Test
    void advertiseSecureChatIgnoresOtherPackets() {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, 121);
        buf.writeBytes(new byte[]{0});
        PlayChat.advertiseSecureChat(776, buf);
        assertEquals(0, buf.getByte(buf.writerIndex() - 1));
        buf.release();
    }
}

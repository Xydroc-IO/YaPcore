package com.yapcore.link.bedrock.downstream;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class JavaPlayInventoryWireTest {

    @Test
    void containerClickUsesProtocolId18() {
        ByteBuf buf = JavaPlayInventoryWire.containerClick(0, 3, 36, 0, 0);
        try {
            assertEquals(JavaPlayWire.SB_CONTAINER_CLICK, McCodec.readVarInt(buf));
            assertEquals(0, McCodec.readVarInt(buf));
            assertEquals(3, McCodec.readVarInt(buf));
            assertEquals(36, buf.readShort());
            assertEquals(0, buf.readByte());
            assertEquals(0, McCodec.readVarInt(buf));
            assertEquals(0, McCodec.readVarInt(buf));
            assertFalse(buf.readBoolean());
        } finally {
            buf.release();
        }
    }

    @Test
    void attackPacketIdRemainsOne() {
        assertEquals(1, JavaPlayWire.SB_ATTACK);
    }

    @Test
    void renameItemUsesProtocolId48() {
        ByteBuf buf = JavaPlayInventoryWire.renameItem("Sharpness VII");
        try {
            assertEquals(JavaPlayWire.SB_RENAME_ITEM, McCodec.readVarInt(buf));
            assertEquals(48, JavaPlayWire.SB_RENAME_ITEM);
            assertEquals("Sharpness VII", McCodec.readString(buf, 32767));
        } finally {
            buf.release();
        }
    }
}

package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4.3 — metadata skip + chunk light. */
class MetadataAndLightTest {

    @Test
    void lightPacketNameDetection() {
        assertTrue(ChunkLightCodec.isLightPacket("light_update"));
        assertTrue(ChunkLightCodec.isLightPacket("update_light"));
    }

    @Test
    void modernishColumnIncludesLightTail() {
        ByteBuf sections = Unpooled.buffer();
        int[] air = new int[ChunkPaletteCodec.SECTION_BLOCKS];
        ChunkPaletteCodec.writePalettedSection(sections, air);
        ByteBuf col = ChunkPaletteCodec.buildModernishColumn(0, 0, sections);
        sections.release();
        // x/z + heightmap + section + block entities + light bitsets
        assertTrue(col.readableBytes() > 2048, "expected full-bright light arrays");
        col.release();
    }

    @Test
    void metadataCopiesByteAndItem() {
        ByteBuf body = Unpooled.buffer();
        McCodec.writeVarInt(body, 42); // entity id
        body.writeByte(0); // index
        McCodec.writeVarInt(body, 0); // byte type
        body.writeByte(7);
        body.writeByte(0xFF);

        ItemRemapper items = new ItemRemapper(ProtocolBand.V26_2, ProtocolBand.V1_21);
        SlotCodec slots = new SlotCodec(ProtocolBand.V26_2, ProtocolBand.V1_21, items, true);
        ByteBuf out = slots.remapEntityMetadata(body, 99);
        assertNotNull(out);
        assertEquals(99, McCodec.readVarInt(out));
        assertEquals(42, McCodec.readVarInt(out));
        assertEquals(0, out.readUnsignedByte());
        assertEquals(0, McCodec.readVarInt(out));
        assertEquals(7, out.readByte());
        assertEquals(0xFF, out.readUnsignedByte());
        out.release();
        body.release();
    }

    @Test
    void metadataCopiesCompoundTagType16() {
        assertTrue(EntityMetadataSkip.skipValue(ProtocolBand.V26_2, 16, Unpooled.buffer().writeByte(0)));
    }
}

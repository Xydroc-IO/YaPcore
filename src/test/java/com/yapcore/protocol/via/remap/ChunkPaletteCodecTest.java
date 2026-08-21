package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.ProtocolBand;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkPaletteCodecTest {

    @Test
    void singleValuedRoundTrip() {
        int[] states = new int[ChunkPaletteCodec.SECTION_BLOCKS];
        java.util.Arrays.fill(states, 42);
        ByteBuf buf = Unpooled.buffer();
        ChunkPaletteCodec.writePalettedSection(buf, states);
        int[] back = ChunkPaletteCodec.readPalettedSection(buf);
        assertEquals(42, back[0]);
        assertEquals(42, back[4095]);
        buf.release();
    }

    @Test
    void legacyToModernProducesReadablePayload() {
        BlockRemapper blocks = new BlockRemapper(ProtocolBand.V1_8, ProtocolBand.V26_2);
        ChunkRemapper remapper = new ChunkRemapper(ProtocolBand.V1_8, ProtocolBand.V26_2, blocks);
        ByteBuf legacy = Unpooled.buffer();
        legacy.writeInt(1);
        legacy.writeInt(2);
        legacy.writeBoolean(true);
        legacy.writeShort(0x0001);
        byte[] section = new byte[ChunkPaletteCodec.SECTION_BLOCKS * 2];
        section[0] = 0x00;
        section[1] = 0x10; // block id 1 meta 0 packed as short
        com.yapcore.protocol.java.codec.McCodec.writeVarInt(legacy, section.length);
        legacy.writeBytes(section);
        ByteBuf modern = remapper.remapClientboundChunk(legacy);
        assertTrue(modern.readableBytes() > 16);
        assertEquals(1, modern.readInt());
        assertEquals(2, modern.readInt());
        modern.release();
        legacy.release();
    }

    @Test
    void modernSingleSectionToLegacy() {
        BlockRemapper blocks = new BlockRemapper(ProtocolBand.V26_2, ProtocolBand.V1_8);
        ChunkRemapper remapper = new ChunkRemapper(ProtocolBand.V26_2, ProtocolBand.V1_8, blocks);
        int[] states = new int[ChunkPaletteCodec.SECTION_BLOCKS];
        java.util.Arrays.fill(states, 1);
        ByteBuf sections = Unpooled.buffer();
        ChunkPaletteCodec.writePalettedSection(sections, states);
        ByteBuf modern = ChunkPaletteCodec.buildModernishColumn(3, 4, sections);
        sections.release();
        ByteBuf legacy = remapper.remapClientboundChunk(modern);
        assertEquals(3, legacy.readInt());
        assertEquals(4, legacy.readInt());
        assertTrue(legacy.readBoolean());
        assertTrue(legacy.readUnsignedShort() != 0);
        modern.release();
        legacy.release();
    }
}

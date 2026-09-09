package com.yapcore.world.schem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for Litematica bit packing (no Bukkit). */
class LitematicImporterTest {

    @Test
    void bitsPerBlockMatchesLitematicaFloor() {
        assertEquals(2, LitematicImporter.bitsPerBlock(1));
        assertEquals(2, LitematicImporter.bitsPerBlock(2));
        assertEquals(2, LitematicImporter.bitsPerBlock(4));
        assertEquals(3, LitematicImporter.bitsPerBlock(5));
        assertEquals(4, LitematicImporter.bitsPerBlock(16));
        assertEquals(5, LitematicImporter.bitsPerBlock(17));
    }

    @Test
    void unpackRoundTripAcrossLongBoundary() {
        int bits = 5;
        int count = 20;
        long[] packed = new long[(count * bits + 63) / 64];
        for (int i = 0; i < count; i++) {
            pack(packed, i, bits, i % 17);
        }
        for (int i = 0; i < count; i++) {
            assertEquals(i % 17, LitematicImporter.unpack(packed, i, bits), "index " + i);
        }
    }

    @Test
    void unpackTwoBitValues() {
        // bits=2: values 0,1,2,3 packed into first long
        long[] data = new long[]{0b11_10_01_00L}; // little-endian bit order per Litematica
        assertEquals(0, LitematicImporter.unpack(data, 0, 2));
        assertEquals(1, LitematicImporter.unpack(data, 1, 2));
        assertEquals(2, LitematicImporter.unpack(data, 2, 2));
        assertEquals(3, LitematicImporter.unpack(data, 3, 2));
    }

    /** Pack palette id into long[] the same way Litematica/Minecraft do. */
    private static void pack(long[] data, int index, int bits, int value) {
        long maxEntryValue = (1L << bits) - 1L;
        value &= (int) maxEntryValue;
        int startOffset = index * bits;
        int startArrIndex = startOffset >> 6;
        int endArrIndex = ((index + 1) * bits - 1) >> 6;
        int startBitOffset = startOffset & 0x3F;
        if (startArrIndex == endArrIndex) {
            data[startArrIndex] = data[startArrIndex] & ~(maxEntryValue << startBitOffset)
                    | ((long) value << startBitOffset);
        } else {
            int endOffset = 64 - startBitOffset;
            data[startArrIndex] = data[startArrIndex] & ~(maxEntryValue << startBitOffset)
                    | (((long) value << startBitOffset) & (~0L));
            data[endArrIndex] = data[endArrIndex] >>> endOffset << endOffset
                    | ((long) value >> endOffset);
        }
    }
}

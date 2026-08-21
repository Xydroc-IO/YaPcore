package com.yapcore.protocol.java.chunk;

import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.world.ChunkColumn;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes {@code level_chunk_with_light} for protocol 776 / Minecraft 26.2.
 */
public final class ChunkPacketEncoder {

    public static final int PACKET_ID = 45; // minecraft:level_chunk_with_light
    /** Network ID of minecraft:plains in YaPcore's 26.2 BIOMES registry dump (sorted). */
    public static final int BIOME_PLAINS = 40;

    private ChunkPacketEncoder() {
    }

    public static ByteBuf encode(ChunkColumn chunk) {
        ByteBuf buf = Unpooled.buffer();
        McCodec.writeVarInt(buf, PACKET_ID);
        buf.writeInt(chunk.chunkX());
        buf.writeInt(chunk.chunkZ());

        ByteBuf chunkData = Unpooled.buffer();
        writeHeightmaps(chunkData, chunk);
        ByteBuf sections = Unpooled.buffer();
        for (int i = 0; i < ChunkColumn.SECTION_COUNT; i++) {
            writeSection(sections, chunk.section(i));
        }
        McCodec.writeVarInt(chunkData, sections.readableBytes());
        chunkData.writeBytes(sections);
        sections.release();
        McCodec.writeVarInt(chunkData, 0); // block entities empty

        buf.writeBytes(chunkData);
        chunkData.release();

        writeFullBrightLight(buf);
        return buf;
    }

    private static void writeHeightmaps(ByteBuf out, ChunkColumn chunk) {
        // Prefixed Array of Heightmap — WORLD_SURFACE(1) + MOTION_BLOCKING(4)
        McCodec.writeVarInt(out, 2);
        writeHeightmap(out, 1, chunk);
        writeHeightmap(out, 4, chunk);
    }

    private static void writeHeightmap(ByteBuf out, int type, ChunkColumn chunk) {
        McCodec.writeVarInt(out, type);
        // world height 384 → values 0..384 → 9 bits; 256 columns
        int bits = 9;
        int valuesPerLong = 64 / bits;
        int longCount = (256 + valuesPerLong - 1) / valuesPerLong;
        long[] data = new long[longCount];
        int idx = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int h = chunk.surfaceHeightLocal(x, z);
                int longIndex = idx / valuesPerLong;
                int bitIndex = (idx % valuesPerLong) * bits;
                data[longIndex] |= ((long) h & ((1L << bits) - 1)) << bitIndex;
                idx++;
            }
        }
        McCodec.writeVarInt(out, data.length);
        for (long v : data) {
            out.writeLong(v);
        }
    }

    private static void writeSection(ByteBuf out, int[] blocks) {
        short nonAir = 0;
        for (int b : blocks) {
            if (b != 0) {
                nonAir++;
            }
        }
        out.writeShort(nonAir);
        writePalettedContainer(out, blocks, true);
        // biomes: 4x4x4 = 64 entries, all plains
        int[] biomes = new int[64];
        for (int i = 0; i < 64; i++) {
            biomes[i] = BIOME_PLAINS;
        }
        writePalettedContainer(out, biomes, false);
    }

    private static void writePalettedContainer(ByteBuf out, int[] values, boolean blocks) {
        // Try single-valued
        int first = values[0];
        boolean single = true;
        for (int v : values) {
            if (v != first) {
                single = false;
                break;
            }
        }
        if (single) {
            out.writeByte(0); // bits per entry
            McCodec.writeVarInt(out, first);
            McCodec.writeVarInt(out, 0); // data array length 0
            return;
        }

        // Indirect palette
        Map<Integer, Integer> paletteIndex = new LinkedHashMap<>();
        List<Integer> palette = new ArrayList<>();
        for (int v : values) {
            if (!paletteIndex.containsKey(v)) {
                paletteIndex.put(v, palette.size());
                palette.add(v);
            }
        }
        int bits = bitsNeeded(palette.size());
        if (blocks) {
            bits = Math.max(4, bits); // block indirect min 4 in modern protocol when not single
            if (bits > 8) {
                // direct global — write values as-is with 15 bits typically; use 15
                writeDirect(out, values, 15);
                return;
            }
        } else {
            bits = Math.max(1, Math.min(3, bits));
            if (palette.size() > 8) {
                writeDirect(out, values, 6); // biome direct
                return;
            }
        }
        out.writeByte(bits);
        McCodec.writeVarInt(out, palette.size());
        for (int id : palette) {
            McCodec.writeVarInt(out, id);
        }
        int[] indices = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            indices[i] = paletteIndex.get(values[i]);
        }
        writePacked(out, indices, bits);
    }

    private static void writeDirect(ByteBuf out, int[] values, int bits) {
        out.writeByte(bits);
        writePacked(out, values, bits);
    }

    private static void writePacked(ByteBuf out, int[] values, int bits) {
        int valuesPerLong = 64 / bits;
        int longCount = (values.length + valuesPerLong - 1) / valuesPerLong;
        long[] data = new long[longCount];
        for (int i = 0; i < values.length; i++) {
            int longIndex = i / valuesPerLong;
            int bitIndex = (i % valuesPerLong) * bits;
            data[longIndex] |= ((long) values[i] & ((1L << bits) - 1)) << bitIndex;
        }
        McCodec.writeVarInt(out, data.length);
        for (long v : data) {
            out.writeLong(v);
        }
    }

    private static int bitsNeeded(int size) {
        int bits = 0;
        int n = size - 1;
        while (n > 0) {
            bits++;
            n >>= 1;
        }
        return Math.max(1, bits);
    }

    private static void writeFullBrightLight(ByteBuf out) {
        // 24 sections + 2 = 26 bits
        int sectionCount = ChunkColumn.SECTION_COUNT + 2;
        long[] skyMask = new long[]{(1L << sectionCount) - 1};
        long[] emptyBlockMask = new long[]{(1L << sectionCount) - 1};
        writeBitSet(out, skyMask);       // sky light mask — all present
        writeBitSet(out, new long[]{0}); // block light mask — none
        writeBitSet(out, new long[]{0}); // empty sky — none (we send data)
        writeBitSet(out, emptyBlockMask); // empty block — all empty

        byte[] full = new byte[2048];
        for (int i = 0; i < full.length; i++) {
            full[i] = (byte) 0xFF; // light level 15
        }
        McCodec.writeVarInt(out, sectionCount); // sky light array count
        for (int i = 0; i < sectionCount; i++) {
            McCodec.writeVarInt(out, 2048);
            out.writeBytes(full);
        }
        McCodec.writeVarInt(out, 0); // block light arrays
    }

    private static void writeBitSet(ByteBuf out, long[] words) {
        McCodec.writeVarInt(out, words.length);
        for (long w : words) {
            out.writeLong(w);
        }
    }
}

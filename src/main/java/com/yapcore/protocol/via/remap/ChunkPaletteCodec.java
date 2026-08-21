package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.codec.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Legacy column sections (id&lt;&lt;4|meta shorts) ↔ simple paletted section payloads.
 * Clean-room — enough for Via Rewind/forward chunk reshape, not full 1.18 light codec.
 */
public final class ChunkPaletteCodec {

    public static final int SECTION_BLOCKS = 16 * 16 * 16; // 4096

    private ChunkPaletteCodec() {
    }

    /** Unpack 4096 big-endian shorts into block states via remapper. */
    public static int[] legacyShortsToStates(byte[] data, int offset, BlockRemapper blocks) {
        int[] states = new int[SECTION_BLOCKS];
        int end = Math.min(offset + SECTION_BLOCKS * 2, data.length);
        int i = 0;
        for (int p = offset; p + 1 < end && i < SECTION_BLOCKS; p += 2, i++) {
            int packed = ((data[p] & 0xFF) << 8) | (data[p + 1] & 0xFF);
            states[i] = blocks.toServerBlockState(packed);
        }
        return states;
    }

    /** Pack states into legacy shorts (client/meta-0). */
    public static byte[] statesToLegacyShorts(int[] states, BlockRemapper blocks) {
        byte[] out = new byte[SECTION_BLOCKS * 2];
        for (int i = 0; i < SECTION_BLOCKS; i++) {
            int legacy = blocks.toClientLegacy(states[i]);
            out[i * 2] = (byte) ((legacy >> 8) & 0xFF);
            out[i * 2 + 1] = (byte) (legacy & 0xFF);
        }
        return out;
    }

    /**
     * Write a compact paletted section: bits=0 single-valued if uniform, else bits=8 direct palette.
     * Layout: bitsPerEntry (ubyte), palette (varint count + varint states), then packed longs.
     */
    public static void writePalettedSection(ByteBuf out, int[] states) {
        int uniform = states[0];
        boolean allSame = true;
        for (int i = 1; i < states.length; i++) {
            if (states[i] != uniform) {
                allSame = false;
                break;
            }
        }
        if (allSame) {
            out.writeByte(0); // single valued
            McCodec.writeVarInt(out, uniform);
            McCodec.writeVarInt(out, 0); // no data
            return;
        }
        // Direct 8-bit indices into a built palette (cap 256 unique)
        java.util.LinkedHashMap<Integer, Integer> palette = new java.util.LinkedHashMap<>();
        int[] indices = new int[states.length];
        for (int i = 0; i < states.length; i++) {
            int state = states[i];
            Integer idx = palette.get(state);
            if (idx == null) {
                if (palette.size() >= 256) {
                    // collapse remainder to air/first
                    idx = 0;
                } else {
                    idx = palette.size();
                    palette.put(state, idx);
                }
            }
            indices[i] = idx;
        }
        out.writeByte(8);
        McCodec.writeVarInt(out, palette.size());
        for (int state : palette.keySet()) {
            McCodec.writeVarInt(out, state);
        }
        // 4096 bytes → 512 longs
        long[] longs = new long[SECTION_BLOCKS / 8];
        for (int i = 0; i < SECTION_BLOCKS; i++) {
            longs[i >> 3] |= ((long) (indices[i] & 0xFF)) << ((i & 7) * 8);
        }
        McCodec.writeVarInt(out, longs.length);
        for (long v : longs) {
            out.writeLong(v);
        }
    }

    /**
     * Best-effort read of one paletted section → 4096 states. Returns null on failure.
     */
    public static int[] readPalettedSection(ByteBuf in) {
        int mark = in.readerIndex();
        try {
            int bits = in.readUnsignedByte();
            if (bits == 0) {
                int state = McCodec.readVarInt(in);
                int dataLen = McCodec.readVarInt(in);
                for (int i = 0; i < dataLen && in.isReadable(); i++) {
                    in.readLong();
                }
                int[] states = new int[SECTION_BLOCKS];
                java.util.Arrays.fill(states, state);
                return states;
            }
            if (bits > 16) {
                in.readerIndex(mark);
                return null;
            }
            int paletteSize = McCodec.readVarInt(in);
            int[] palette = new int[Math.max(paletteSize, 1)];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = McCodec.readVarInt(in);
            }
            int longCount = McCodec.readVarInt(in);
            long[] data = new long[longCount];
            for (int i = 0; i < longCount; i++) {
                data[i] = in.readLong();
            }
            int[] states = new int[SECTION_BLOCKS];
            if (bits == 8 && longCount >= SECTION_BLOCKS / 8) {
                for (int i = 0; i < SECTION_BLOCKS; i++) {
                    int idx = (int) ((data[i >> 3] >>> ((i & 7) * 8)) & 0xFF);
                    states[i] = idx < palette.length ? palette[idx] : 0;
                }
                return states;
            }
            // Generic bit unpack
            int mask = (1 << bits) - 1;
            int valuesPerLong = bits == 0 ? 1 : (64 / bits);
            for (int i = 0; i < SECTION_BLOCKS; i++) {
                int longIndex = i / Math.max(valuesPerLong, 1);
                int inLong = i % Math.max(valuesPerLong, 1);
                if (longIndex >= data.length) {
                    break;
                }
                int idx = (int) ((data[longIndex] >>> (inLong * bits)) & mask);
                states[i] = idx < palette.length ? palette[idx] : 0;
            }
            return states;
        } catch (Exception e) {
            in.readerIndex(mark);
            return null;
        }
    }

    /** Build a legacy 1.8-style column body (no packet id). */
    public static ByteBuf buildLegacyColumn(int chunkX, int chunkZ, int primaryMask, byte[] blockData) {
        ByteBuf out = Unpooled.buffer(16 + blockData.length);
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        out.writeBoolean(true);
        out.writeShort(primaryMask & 0xFFFF);
        McCodec.writeVarInt(out, blockData.length);
        out.writeBytes(blockData);
        return out;
    }

    /** Minimal modern-ish chunk body: X/Z + empty heightmaps + one paletted section buffer. */
    public static ByteBuf buildModernishColumn(int chunkX, int chunkZ, ByteBuf sectionsPayload) {
        ByteBuf out = Unpooled.buffer(64 + sectionsPayload.readableBytes());
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        // Heightmaps: empty NBT compound (0x0a type + empty name + end) — best-effort
        out.writeByte(0x0a);
        out.writeShort(0);
        out.writeByte(0x00);
        McCodec.writeVarInt(out, sectionsPayload.readableBytes());
        out.writeBytes(sectionsPayload, sectionsPayload.readerIndex(), sectionsPayload.readableBytes());
        McCodec.writeVarInt(out, 0); // block entities
        return out;
    }
}

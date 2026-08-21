package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.codec.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * P4.3 — Mid-band chunk light harden for {@code level_chunk_with_light} / {@code light_update}.
 * <p>
 * Product mid clients (1.20.2+) already speak paletted columns; when a remap path rebuilds
 * a modernish column we append full-bright sky light so clients do not render black voids.
 * Standalone light_update packets are ID-rewritten with body passthrough (same layout 764–776).
 */
public final class ChunkLightCodec {

    /** 24 world sections + bottom/top border = 26 (matches ChunkPacketEncoder). */
    public static final int LIGHT_SECTION_COUNT = 26;
    public static final int LIGHT_BYTES = 2048; // 16*16*16 / 2 nibbles

    private ChunkLightCodec() {
    }

    /** Append full-bright sky light + empty block light (BitSet + arrays). */
    public static void writeFullBright(ByteBuf out) {
        long mask = (1L << LIGHT_SECTION_COUNT) - 1;
        writeBitSet(out, new long[]{mask});          // sky present
        writeBitSet(out, new long[]{0});             // block present
        writeBitSet(out, new long[]{0});             // empty sky
        writeBitSet(out, new long[]{mask});          // empty block

        byte[] full = new byte[LIGHT_BYTES];
        java.util.Arrays.fill(full, (byte) 0xFF);
        McCodec.writeVarInt(out, LIGHT_SECTION_COUNT);
        for (int i = 0; i < LIGHT_SECTION_COUNT; i++) {
            McCodec.writeVarInt(out, LIGHT_BYTES);
            out.writeBytes(full);
        }
        McCodec.writeVarInt(out, 0); // block light arrays
    }

    /**
     * If {@code column} has no trailing light (legacy rebuild), append full-bright.
     * Heuristic: packets shorter than heightmaps+sections+light floor get light appended.
     */
    public static ByteBuf ensureLightTail(ByteBuf columnBody) {
        int mark = columnBody.readerIndex();
        try {
            // Peek: after x/z (8) + heightmaps + sections, light starts with a BitSet varint length
            // For our buildModernishColumn output there is NO light — always append.
            ByteBuf out = Unpooled.buffer(columnBody.readableBytes() + LIGHT_SECTION_COUNT * (LIGHT_BYTES + 8) + 64);
            out.writeBytes(columnBody, columnBody.readerIndex(), columnBody.readableBytes());
            writeFullBright(out);
            return out;
        } catch (Exception e) {
            columnBody.readerIndex(mark);
            return columnBody.retainedDuplicate();
        }
    }

    public static boolean isLightPacket(String canonicalName) {
        if (canonicalName == null) {
            return false;
        }
        String n = canonicalName.toLowerCase();
        return n.equals("light_update") || n.equals("update_light")
                || n.contains("light_update");
    }

    private static void writeBitSet(ByteBuf out, long[] words) {
        McCodec.writeVarInt(out, words.length);
        for (long w : words) {
            out.writeLong(w);
        }
    }
}

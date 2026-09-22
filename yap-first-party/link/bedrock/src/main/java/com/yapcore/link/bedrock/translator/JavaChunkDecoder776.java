package com.yapcore.link.bedrock.translator;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Best-effort decoder for JE {@code level_chunk_with_light} body (proto 776) → 24×4096 global state ids.
 */
public final class JavaChunkDecoder776 {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    private static final int SECTION_BLOCKS = 16 * 16 * 16;
    private static final int MAX_SECTIONS = 24;

    private JavaChunkDecoder776() {
    }

    /**
     * @param body chunk payload after chunkX/chunkZ (heightmaps + sections + block entities + light)
     * @return {@code int[24][4096]} section-major JE global ids, or null on failure
     */
    public static int[][] decodeColumn(ByteBuf body) {
        if (body == null || !body.isReadable()) {
            return null;
        }
        int mark = body.readerIndex();
        try {
            skipHeightmaps(body);
            if (!body.isReadable()) {
                return null;
            }
            int dataSize = McCodec.readVarInt(body);
            if (dataSize < 0 || dataSize > body.readableBytes()) {
                body.readerIndex(mark);
                return null;
            }
            ByteBuf sections = body.readSlice(dataSize);
            List<int[]> parsed = new ArrayList<>(MAX_SECTIONS);
            while (sections.isReadable() && parsed.size() < MAX_SECTIONS) {
                int[] sec = readSection776(sections);
                if (sec == null) {
                    break;
                }
                parsed.add(sec);
            }
            if (parsed.isEmpty()) {
                return null;
            }
            int[][] column = new int[MAX_SECTIONS][];
            Arrays.fill(column, null);
            int start = Math.max(0, MAX_SECTIONS - parsed.size());
            for (int i = 0; i < parsed.size(); i++) {
                column[start + i] = parsed.get(i);
            }
            for (int i = 0; i < MAX_SECTIONS; i++) {
                if (column[i] == null) {
                    column[i] = new int[SECTION_BLOCKS];
                }
            }
            return column;
        } catch (Exception e) {
            LOG.fine("JavaChunkDecoder776 fail: " + e.getMessage());
            body.readerIndex(mark);
            return null;
        }
    }

    private static int[] readSection776(ByteBuf in) {
        if (in.readableBytes() < 4) {
            return null;
        }
        in.readShort(); // block count
        if (in.readableBytes() < 2) {
            return null;
        }
        in.readShort(); // fluid count (26.1+)
        int[] blocks = readPalettedContainer(in, SECTION_BLOCKS, true);
        if (blocks == null) {
            return null;
        }
        readPalettedContainer(in, 64, false); // biomes — discard
        return blocks;
    }

    /** Proto 776: no size prefix on packed long arrays (770+). */
    private static int[] readPalettedContainer(ByteBuf in, int entries, boolean blocks) {
        if (!in.isReadable()) {
            return null;
        }
        int mark = in.readerIndex();
        try {
            int bits = in.readUnsignedByte();
            if (bits == 0) {
                int value = McCodec.readVarInt(in);
                int[] states = new int[entries];
                Arrays.fill(states, value);
                return states;
            }
            if (bits > 16) {
                in.readerIndex(mark);
                return null;
            }
            int maxIndirect = blocks ? 8 : 3;
            int[] palette;
            if (bits <= maxIndirect) {
                int paletteSize = McCodec.readVarInt(in);
                palette = new int[Math.max(paletteSize, 1)];
                for (int i = 0; i < paletteSize; i++) {
                    palette[i] = McCodec.readVarInt(in);
                }
            } else {
                palette = null;
            }
            int valuesPerLong = Math.max(1, 64 / Math.max(bits, 1));
            int longCount = (entries + valuesPerLong - 1) / valuesPerLong;
            long[] data = new long[longCount];
            for (int i = 0; i < longCount; i++) {
                if (!in.isReadable()) {
                    in.readerIndex(mark);
                    return null;
                }
                data[i] = in.readLong();
            }
            int mask = (1 << bits) - 1;
            int[] states = new int[entries];
            for (int i = 0; i < entries; i++) {
                int longIndex = i / valuesPerLong;
                int inLong = i % valuesPerLong;
                if (longIndex >= data.length) {
                    states[i] = 0;
                    continue;
                }
                int idx = (int) ((data[longIndex] >>> (inLong * bits)) & mask);
                if (palette != null) {
                    states[i] = idx < palette.length ? palette[idx] : 0;
                } else {
                    states[i] = idx;
                }
            }
            return states;
        } catch (Exception e) {
            in.readerIndex(mark);
            return null;
        }
    }

    private static void skipHeightmaps(ByteBuf in) {
        if (!in.isReadable()) {
            return;
        }
        int peek = in.getUnsignedByte(in.readerIndex());
        if (peek == 0x0a) {
            skipNbtTag(in);
            return;
        }
        int count = McCodec.readVarInt(in);
        for (int i = 0; i < count && in.isReadable(); i++) {
            McCodec.readVarInt(in); // type
            int longs = McCodec.readVarInt(in);
            in.skipBytes(Math.min(longs * 8, in.readableBytes()));
        }
    }

    private static void skipNbtTag(ByteBuf in) {
        byte type = in.readByte();
        if (type == 0) {
            return;
        }
        if ((type & 0xFF) == 0x0a) {
            skipNbtUtf(in);
        }
        skipNbtPayload(in, type);
    }

    private static void skipNbtUtf(ByteBuf in) {
        if (in.readableBytes() < 2) {
            return;
        }
        int len = in.readUnsignedShort();
        in.skipBytes(Math.max(0, Math.min(len, in.readableBytes())));
    }

    private static void skipNbtPayload(ByteBuf in, int type) {
        switch (type) {
            case 1 -> in.skipBytes(1);
            case 2 -> in.skipBytes(2);
            case 3, 5 -> in.skipBytes(4); // int / float
            case 4, 6 -> in.skipBytes(8); // long / double
            case 7 -> {
                int len = in.readInt();
                in.skipBytes(Math.max(0, Math.min(len, in.readableBytes())));
            }
            case 8 -> skipNbtUtf(in);
            case 9 -> {
                int elemType = in.readUnsignedByte();
                int len = in.readInt();
                for (int i = 0; i < len; i++) {
                    skipNbtPayload(in, elemType);
                }
            }
            case 10 -> {
                while (true) {
                    byte fieldType = in.readByte();
                    if (fieldType == 0) {
                        break;
                    }
                    skipNbtUtf(in);
                    skipNbtPayload(in, fieldType);
                }
            }
            case 11 -> {
                int len = in.readInt();
                in.skipBytes(Math.max(0, Math.min(len * 4, in.readableBytes())));
            }
            case 12 -> {
                int len = in.readInt();
                in.skipBytes(Math.max(0, Math.min(len * 8, in.readableBytes())));
            }
            default -> {
                // unknown — bail
            }
        }
    }

    /** Position {@code body} at the block-entity count. False if the chunk layout is not recognized. */
    static boolean seekBlockEntities(ByteBuf body) {
        if (body == null || !body.isReadable()) {
            return false;
        }
        try {
            skipHeightmaps(body);
            if (!body.isReadable()) {
                return false;
            }
            int dataSize = McCodec.readVarInt(body);
            if (dataSize < 0 || dataSize > body.readableBytes()) {
                return false;
            }
            body.skipBytes(dataSize);
            return body.isReadable();
        } catch (RuntimeException e) {
            return false;
        }
    }
}

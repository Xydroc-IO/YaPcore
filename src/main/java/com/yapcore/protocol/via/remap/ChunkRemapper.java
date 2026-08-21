package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import com.yapcore.protocol.via.catalog.CatalogStore;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Chunk data reshape between legacy columns and paletted sections.
 */
public final class ChunkRemapper {

    private final ProtocolBand from;
    private final ProtocolBand to;
    private final BlockRemapper blocks;
    private final CatalogStore catalogs = CatalogStore.get();

    public ChunkRemapper(ProtocolBand from, ProtocolBand to, BlockRemapper blocks) {
        this.from = from;
        this.to = to;
        this.blocks = blocks;
    }

    public ByteBuf remapClientboundChunk(ByteBuf body) {
        if (from == to) {
            return body.retainedDuplicate();
        }
        String fromFmt = catalogs.sectionFormat(from);
        String toFmt = catalogs.sectionFormat(to);
        // Mid-modern paletted↔paletted (1.20.2+ ↔ 26.2): section layout matches —
        // do NOT scan the packet as packed block shorts (corrupts heightmaps/light).
        if ("paletted".equals(fromFmt) && "paletted".equals(toFmt)
                && from.ordinal() >= ProtocolBand.V1_20_2.ordinal()
                && to.ordinal() >= ProtocolBand.V1_20_2.ordinal()) {
            return body.retainedDuplicate();
        }
        if ("legacy".equals(fromFmt) || from.ordinal() <= ProtocolBand.V1_8.ordinal()) {
            return legacyColumnToModern(body);
        }
        if ("legacy".equals(toFmt) || to.ordinal() <= ProtocolBand.V1_12.ordinal()) {
            return modernToLegacy(body);
        }
        return remapPackedBlockShorts(body);
    }

    private ByteBuf legacyColumnToModern(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            int chunkX = body.readInt();
            int chunkZ = body.readInt();
            boolean groundUp = body.readBoolean();
            int mask = body.readUnsignedShort();
            int size = McCodec.readVarInt(body);
            byte[] data = new byte[Math.min(size, body.readableBytes())];
            body.readBytes(data);

            ByteBuf sections = Unpooled.buffer(data.length + 256);
            int offset = 0;
            for (int section = 0; section < 16; section++) {
                if ((mask & (1 << section)) == 0) {
                    continue;
                }
                if (offset + ChunkPaletteCodec.SECTION_BLOCKS * 2 > data.length) {
                    break;
                }
                int[] states = ChunkPaletteCodec.legacyShortsToStates(data, offset, blocks);
                offset += ChunkPaletteCodec.SECTION_BLOCKS * 2;
                // skip nibble light if present in remaining stream — not required for palette write
                ChunkPaletteCodec.writePalettedSection(sections, states);
            }
            if (sections.readableBytes() == 0) {
                // empty chunk — single air section
                int[] air = new int[ChunkPaletteCodec.SECTION_BLOCKS];
                ChunkPaletteCodec.writePalettedSection(sections, air);
            }
            ByteBuf out = ChunkPaletteCodec.buildModernishColumn(chunkX, chunkZ, sections);
            sections.release();
            if (!groundUp) {
                // keep best-effort; modern clients ignore groundUp
            }
            return out;
        } catch (Exception e) {
            body.readerIndex(mark);
            return body.retainedDuplicate();
        }
    }

    private ByteBuf modernToLegacy(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            int chunkX = body.readInt();
            int chunkZ = body.readInt();

            // Skip heightmaps NBT if present (tag compound)
            if (body.isReadable() && body.getByte(body.readerIndex()) == 0x0a) {
                skipNbt(body);
            }

            byte[] blockBlob = new byte[16 * ChunkPaletteCodec.SECTION_BLOCKS * 2];
            int mask = 0;
            int sectionsParsed = 0;

            if (body.isReadable()) {
                // Prefer size-prefixed section buffer
                int dataSize = -1;
                int before = body.readerIndex();
                try {
                    dataSize = McCodec.readVarInt(body);
                } catch (Exception e) {
                    body.readerIndex(before);
                }
                ByteBuf sectionBuf = body;
                if (dataSize > 0 && dataSize <= body.readableBytes()) {
                    sectionBuf = body.readSlice(dataSize);
                }
                while (sectionBuf.isReadable() && sectionsParsed < 16) {
                    int[] states = ChunkPaletteCodec.readPalettedSection(sectionBuf);
                    if (states == null) {
                        break;
                    }
                    byte[] shorts = ChunkPaletteCodec.statesToLegacyShorts(states, blocks);
                    System.arraycopy(shorts, 0, blockBlob,
                            sectionsParsed * ChunkPaletteCodec.SECTION_BLOCKS * 2, shorts.length);
                    mask |= (1 << sectionsParsed);
                    sectionsParsed++;
                }
            }

            if (mask == 0) {
                // Fallback: remap any trailing short pairs we can see
                body.readerIndex(mark);
                body.skipBytes(8); // x/z
                byte[] rest = new byte[body.readableBytes()];
                body.readBytes(rest);
                int n = Math.min(rest.length / 2, ChunkPaletteCodec.SECTION_BLOCKS);
                for (int i = 0; i < n; i++) {
                    int packed = ((rest[i * 2] & 0xFF) << 8) | (rest[i * 2 + 1] & 0xFF);
                    int legacy = blocks.toClientLegacy(packed);
                    blockBlob[i * 2] = (byte) ((legacy >> 8) & 0xFF);
                    blockBlob[i * 2 + 1] = (byte) (legacy & 0xFF);
                }
                mask = n > 0 ? 1 : 0xFFFF;
                int len = mask == 0xFFFF
                        ? 16 * ChunkPaletteCodec.SECTION_BLOCKS * 2
                        : ChunkPaletteCodec.SECTION_BLOCKS * 2;
                byte[] trimmed = new byte[len];
                System.arraycopy(blockBlob, 0, trimmed, 0, Math.min(len, blockBlob.length));
                return ChunkPaletteCodec.buildLegacyColumn(chunkX, chunkZ, mask, trimmed);
            }

            int used = Integer.bitCount(mask) * ChunkPaletteCodec.SECTION_BLOCKS * 2;
            byte[] trimmed = new byte[used];
            System.arraycopy(blockBlob, 0, trimmed, 0, used);
            return ChunkPaletteCodec.buildLegacyColumn(chunkX, chunkZ, mask, trimmed);
        } catch (Exception e) {
            body.readerIndex(mark);
            return body.retainedDuplicate();
        }
    }

    private static void skipNbt(ByteBuf buf) {
        // Minimal NBT skip for compound root
        byte type = buf.readByte();
        if (type != 0x0a) {
            return;
        }
        buf.readShort(); // name length (usually 0)
        skipNbtPayload(buf, type);
    }

    private static void skipNbtPayload(ByteBuf buf, byte type) {
        switch (type) {
            case 0x00 -> {
            }
            case 0x01 -> buf.skipBytes(1);
            case 0x02 -> buf.skipBytes(2);
            case 0x03, 0x05 -> buf.skipBytes(4);
            case 0x04, 0x06 -> buf.skipBytes(8);
            case 0x07 -> { // byte array
                int len = buf.readInt();
                buf.skipBytes(Math.max(0, len));
            }
            case 0x08 -> { // string
                int len = buf.readUnsignedShort();
                buf.skipBytes(len);
            }
            case 0x09 -> { // list
                byte elem = buf.readByte();
                int len = buf.readInt();
                for (int i = 0; i < len; i++) {
                    skipNbtPayload(buf, elem);
                }
            }
            case 0x0a -> { // compound
                while (buf.isReadable()) {
                    byte t = buf.readByte();
                    if (t == 0x00) {
                        break;
                    }
                    int nameLen = buf.readUnsignedShort();
                    buf.skipBytes(nameLen);
                    skipNbtPayload(buf, t);
                }
            }
            case 0x0b -> {
                int len = buf.readInt();
                buf.skipBytes(len * 4);
            }
            case 0x0c -> {
                int len = buf.readInt();
                buf.skipBytes(len * 8);
            }
            default -> {
            }
        }
    }

    private ByteBuf remapPackedBlockShorts(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            byte[] data = new byte[body.readableBytes()];
            body.readBytes(data);
            int remapped = 0;
            for (int i = 0; i + 1 < data.length; i += 2) {
                int packed = ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
                if (packed == 0) {
                    continue;
                }
                int mapped = blocks.toServerBlockState(packed);
                if (mapped != packed) {
                    data[i] = (byte) ((mapped >> 8) & 0xFF);
                    data[i + 1] = (byte) (mapped & 0xFF);
                    remapped++;
                }
            }
            if (remapped == 0) {
                body.readerIndex(mark);
                return body.retainedDuplicate();
            }
            return Unpooled.wrappedBuffer(data);
        } catch (Exception e) {
            body.readerIndex(mark);
            return body.retainedDuplicate();
        }
    }

    public BlockRemapper blocks() {
        return blocks;
    }
}

package com.yapcore.crossplay.bedrock.codec;

import com.yapcore.crossplay.bedrock.BedrockPacketIds;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import static com.yapcore.crossplay.bedrock.codec.BedrockCodecBinary.*;

/**
 * LevelChunk / hashed-column builders (split from {@link BedrockWorldCodec} for ≤500-line gate).
 */
final class BedrockWorldChunkCodec {
    private BedrockWorldChunkCodec() {}

    /** Air / dirt / stone / grass_block / bedrock defaultState (prismarine-registry bedrock_1.21.50). */
    static final int STATE_AIR = 11261;
    static final int STATE_DIRT = 8805;
    static final int STATE_STONE = 2325;
    static final int STATE_GRASS = 9981;
    static final int STATE_BEDROCK = 11785;
    private static final int BIOME_PLAINS = 1;

    static ByteBuf levelChunkEmpty(int chunkX, int chunkZ) {
        return levelChunkEmpty(chunkX, chunkZ, 2207);
    }

    static ByteBuf levelChunkEmpty(int chunkX, int chunkZ, int protocol) {
        // Hand-rolled — CloudburstJoinCodec.levelChunkEmpty preferred in finishLogin.
        return levelChunkMarker(chunkX, chunkZ, BedrockWorldCodec.usesLevelChunkV2168(protocol));
    }

    static ByteBuf levelChunkMarker(int chunkX, int chunkZ) {
        return levelChunkMarker(chunkX, chunkZ, true);
    }

    static ByteBuf levelChunkMarker(int chunkX, int chunkZ, int protocol) {
        return levelChunkMarker(chunkX, chunkZ, BedrockWorldCodec.usesLevelChunkV2168(protocol));
    }

    /**
     * LevelChunk header matching Cloudburst:
     * <ul>
     *   <li>v2168+: SubChunkLimit Optional (bool) + blob-hash count always</li>
     *   <li>older: cache_enabled then blobs only if cache on</li>
     * </ul>
     */
    /**
     * Geyser {@code ChunkUtils.sendEmptyChunk}: sub_chunk_count=0 but payload still carries
     * full-height biome storages (singleton palette) + border byte. Empty payload crashes
     * modern clients that expect biomes even when no block subchunks are present.
     */
    private static ByteBuf levelChunkMarker(int chunkX, int chunkZ, boolean v2168) {
        final int sections = 24; // overworld -64..320
        ByteBuf payload = Unpooled.buffer(sections + 8);
        // Singleton biome palette matching Geyser EMPTY_BIOME_DATA (V0 runtime header + id 0)
        payload.writeByte(1); // (0<<1)|1
        writeSignedVarInt(payload, 0); // palette entry 0 (no size varint for singleton)
        byte marker = (byte) ((127 << 1) | 1); // carry previous biome
        for (int i = 0; i < sections - 1; i++) {
            payload.writeByte(marker);
        }
        payload.writeByte(0); // border blocks
        ByteBuf out = Unpooled.buffer(payload.readableBytes() + 32);
        writeUnsignedVarInt(out, BedrockPacketIds.LEVEL_CHUNK.id);
        writeZigZag32(out, chunkX);
        writeZigZag32(out, chunkZ);
        writeZigZag32(out, 0); // dimension (v649+)
        writeUnsignedVarInt(out, 0); // sub_chunk_count (Geyser empty = 0)
        writeLevelChunkTrailer(out, v2168, payload.readableBytes());
        out.writeBytes(payload);
        payload.release();
        return out;
    }

    static ByteBuf levelChunkFlat(int chunkX, int chunkZ) {
        return levelChunkFlatWithHeader(chunkX, chunkZ, true);
    }

    /** Cloudburst-aligned: v2168 header when {@code protocol >= 2168}. */
    static ByteBuf levelChunkFlat(int chunkX, int chunkZ, int protocol) {
        return levelChunkFlatWithHeader(chunkX, chunkZ, BedrockWorldCodec.usesLevelChunkV2168(protocol));
    }

    /**
     * Pre-v2168 LevelChunk header (Cloudburst v486/v649 through ~1001):
     * count → cache_enabled → (blobs if cache) → payload.
     */
    static ByteBuf levelChunkFlatLegacy(int chunkX, int chunkZ) {
        return levelChunkFlatWithHeader(chunkX, chunkZ, false);
    }

    /** Trailer after sub_chunk_count: Optional limit (v2168+) + cache + blob ids + payload length. */
    private static void writeLevelChunkTrailer(ByteBuf out, boolean v2168, int payloadBytes) {
        if (v2168) {
            out.writeBoolean(false); // SubChunkLimit Optional = absent (Cloudburst writeOptional)
        }
        out.writeBoolean(false); // cache_enabled
        if (v2168) {
            writeUnsignedVarInt(out, 0); // blob hash count ALWAYS present in v2168
        }
        writeUnsignedVarInt(out, payloadBytes);
    }

    private static ByteBuf levelChunkFlatWithHeader(int chunkX, int chunkZ, boolean v2168) {
        // Overworld -64..320 → 24 subchunks; index 0 = y -64..-49, index 8 = y 64..79
        final int sections = 24;
        ByteBuf payload = Unpooled.buffer(sections * 64 + 256);
        for (int i = 0; i < sections; i++) {
            int absY0 = -64 + i * 16;
            if (absY0 == -64) {
                writeLayeredSubChunk(payload, y -> y == 0 ? STATE_BEDROCK : STATE_STONE);
            } else if (absY0 == 48) {
                writeLayeredSubChunk(payload, y -> y >= 14 ? STATE_DIRT : STATE_STONE);
            } else if (absY0 == 64) {
                writeLayeredSubChunk(payload, y -> y == 0 ? STATE_GRASS : STATE_AIR);
            } else if (absY0 < 48) {
                writeUniformSubChunk(payload, STATE_STONE);
            } else {
                writeUniformSubChunk(payload, STATE_AIR);
            }
        }
        // Biomes: one plains section + 0xFF reuse for remaining height (1.18+)
        writeUniformBiomeStorage(payload, BIOME_PLAINS);
        for (int i = 1; i < sections; i++) {
            payload.writeByte(0xFF);
        }
        payload.writeByte(0); // border blocks length

        ByteBuf out = Unpooled.buffer(payload.readableBytes() + 32);
        writeUnsignedVarInt(out, BedrockPacketIds.LEVEL_CHUNK.id);
        writeZigZag32(out, chunkX);
        writeZigZag32(out, chunkZ);
        writeZigZag32(out, 0); // dimension
        writeUnsignedVarInt(out, sections); // sub_chunk_count
        writeLevelChunkTrailer(out, v2168, payload.readableBytes());
        out.writeBytes(payload);
        payload.release();
        return out;
    }

    /**
     * Encode a Paper (or other) column: {@code states[section][4096]} hashed runtime ids,
     * section 0 = y −64..−49, XZY index {@code (x<<8)|(z<<4)|localY}.
     */
    static ByteBuf levelChunkFromColumn(int chunkX, int chunkZ, int[][] states) {
        return levelChunkFromColumn(chunkX, chunkZ, states, true);
    }

    static ByteBuf levelChunkFromColumn(int chunkX, int chunkZ, int[][] states, int protocol) {
        return levelChunkFromColumn(chunkX, chunkZ, states, BedrockWorldCodec.usesLevelChunkV2168(protocol));
    }

    /**
     * Hashed-column payload only (subchunks + biomes + border) for Cloudburst
     * {@code LevelChunkPacket#setData}. Matches StartGame {@code blockNetworkIdsHashed}.
     */
    static ByteBuf hashedColumnPayload(int[][] states) {
        if (states == null || states.length == 0) {
            ByteBuf empty = Unpooled.buffer(26);
            empty.writeByte(1);
            empty.writeByte(0);
            for (int i = 0; i < 23; i++) {
                empty.writeByte(0xFF);
            }
            empty.writeByte(0);
            return empty;
        }
        final int sections = Math.min(24, states.length);
        ByteBuf payload = Unpooled.buffer(sections * 128 + 256);
        for (int i = 0; i < sections; i++) {
            int[] sec = states[i];
            if (sec == null || sec.length < 4096) {
                writeUniformSubChunk(payload, STATE_AIR);
                continue;
            }
            int first = sec[0];
            boolean uniform = true;
            for (int j = 1; j < 4096; j++) {
                if (sec[j] != first) {
                    uniform = false;
                    break;
                }
            }
            if (uniform) {
                writeUniformSubChunk(payload, first);
            } else {
                writePaletteSubChunk(payload, sec);
            }
        }
        writeUniformBiomeStorage(payload, BIOME_PLAINS);
        for (int i = 1; i < sections; i++) {
            payload.writeByte(0xFF);
        }
        payload.writeByte(0);
        return payload;
    }

    private static ByteBuf levelChunkFromColumn(int chunkX, int chunkZ, int[][] states, boolean v2168) {
        if (states == null || states.length == 0) {
            return levelChunkFlatWithHeader(chunkX, chunkZ, v2168);
        }
        final int sections = Math.min(24, states.length);
        ByteBuf payload = hashedColumnPayload(states);

        ByteBuf out = Unpooled.buffer(payload.readableBytes() + 32);
        writeUnsignedVarInt(out, BedrockPacketIds.LEVEL_CHUNK.id);
        writeZigZag32(out, chunkX);
        writeZigZag32(out, chunkZ);
        writeZigZag32(out, 0);
        writeUnsignedVarInt(out, sections);
        writeLevelChunkTrailer(out, v2168, payload.readableBytes());
        out.writeBytes(payload);
        payload.release();
        return out;
    }

    /** SubChunk v8 + 1 layer, single-value palette (1.18+ runtime short form). */
    private static void writeUniformSubChunk(ByteBuf out, int runtimeStateId) {
        out.writeByte(8); // version
        out.writeByte(1); // storage count
        out.writeByte(1); // bits=0 | network
        writeSignedVarInt(out, runtimeStateId); // zigzag state only (no palette size)
    }

    /**
     * Real multi-entry network palette (Paletted4): {@code localY -> hashed state}.
     * Block indices packed XZY into LE words.
     */
    private static void writeLayeredSubChunk(ByteBuf out, java.util.function.IntUnaryOperator localYToState) {
        out.writeByte(8);
        out.writeByte(1);
        out.writeByte(0x09); // bits=4 | network
        int[] palette = new int[8];
        int paletteSize = 0;
        int[] indices = new int[4096];
        for (int y = 0; y < 16; y++) {
            int state = localYToState.applyAsInt(y);
            int pal = -1;
            for (int p = 0; p < paletteSize; p++) {
                if (palette[p] == state) {
                    pal = p;
                    break;
                }
            }
            if (pal < 0) {
                pal = paletteSize;
                palette[paletteSize++] = state;
            }
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    indices[(x << 8) | (z << 4) | y] = pal;
                }
            }
        }
        for (int w = 0; w < 512; w++) {
            int word = 0;
            for (int b = 0; b < 8; b++) {
                word |= (indices[w * 8 + b] & 0xF) << (b * 4);
            }
            out.writeIntLE(word);
        }
        writeUnsignedVarInt(out, paletteSize);
        for (int p = 0; p < paletteSize; p++) {
            writeSignedVarInt(out, palette[p]);
        }
    }

    /** Arbitrary XZY column section (bits adaptive 1–8). */
    private static void writePaletteSubChunk(ByteBuf out, int[] states4096) {
        // Build palette
        int[] palette = new int[256];
        int paletteSize = 0;
        int[] indices = new int[4096];
        for (int i = 0; i < 4096; i++) {
            int state = states4096[i];
            int pal = -1;
            for (int p = 0; p < paletteSize; p++) {
                if (palette[p] == state) {
                    pal = p;
                    break;
                }
            }
            if (pal < 0) {
                if (paletteSize >= palette.length) {
                    // too many unique — fall back to stone uniform
                    writeUniformSubChunk(out, STATE_STONE);
                    return;
                }
                pal = paletteSize;
                palette[paletteSize++] = state;
            }
            indices[i] = pal;
        }
        if (paletteSize == 1) {
            writeUniformSubChunk(out, palette[0]);
            return;
        }
        int bits = 1;
        while ((1 << bits) < paletteSize && bits < 8) {
            bits++;
        }
        if (bits == 3) {
            bits = 4; // Bedrock skips 3
        }
        if (bits > 4 && bits < 8) {
            bits = 8;
        }
        out.writeByte(8);
        out.writeByte(1);
        out.writeByte((bits << 1) | 1);
        int blocksPerWord = Math.max(1, 32 / bits);
        int words = (4096 + blocksPerWord - 1) / blocksPerWord;
        for (int w = 0; w < words; w++) {
            int word = 0;
            for (int b = 0; b < blocksPerWord; b++) {
                int idx = w * blocksPerWord + b;
                if (idx >= 4096) {
                    break;
                }
                word |= (indices[idx] & ((1 << bits) - 1)) << (b * bits);
            }
            out.writeIntLE(word);
        }
        writeSignedVarInt(out, paletteSize);
        for (int p = 0; p < paletteSize; p++) {
            writeSignedVarInt(out, palette[p]);
        }
    }

    /** Biome single-value runtime: type byte + (id << 1) as unsigned varint. */
    private static void writeUniformBiomeStorage(ByteBuf out, int biomeId) {
        out.writeByte(1); // (0<<1)|1
        writeUnsignedVarInt(out, biomeId << 1);
    }
}

package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.session.LinkBedrockSession;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;

/**
 * Encodes Bedrock runtime palette columns into {@link LevelChunkPacket} (non-hashed join path).
 *
 * <p>Wire layout matches chassis {@code BedrockWorldCodec} / Geyser {@code BlockStorage}:
 * <ul>
 *   <li>SubChunk v8 + 1 storage</li>
 *   <li>Network header {@code (bits << 1) | 1} — never {@code bits | 0x10}</li>
 *   <li>BitArray as little-endian <b>32-bit</b> words (not JE 64-bit longs)</li>
 *   <li>Palette size + entries as zigzag varints (Cloudburst {@code VarInts.writeInt})</li>
 *   <li>JE YZX indices remapped to Bedrock XZY</li>
 *   <li>Biome storages: singleton network header + zigzag id, then {@code 0xFF} reuse</li>
 * </ul>
 */
public final class LinkBedrockChunkEncoder {

    private static final int BIOME_PLAINS = 0;
    private static final int SECTION_BLOCKS = 4096;
    /** Test-only sentinel when no session palette is available. */
    private static final int FALLBACK_AIR = 0;
    private static final int FALLBACK_STONE = 1;

    /** Soft upper bound for a single non-hashed overworld column payload (~24 sections). */
    static final int MAX_COLUMN_PAYLOAD_BYTES = 512 * 1024;
    /** Absolute floor: 24×uniform air (3B) + biome singleton + 23×0xFF + border ≈ 99B. */
    static final int MIN_COLUMN_PAYLOAD_BYTES = 16;

    private LinkBedrockChunkEncoder() {
    }

    public static LevelChunkPacket encodeColumn(
            LinkBedrockSession session, int chunkX, int chunkZ, int[][] runtimeSections) {
        if (runtimeSections == null || runtimeSections.length == 0) {
            return null;
        }
        // Overworld -64..320 → full dimension subchunk count (must match biomes + StartGame height).
        int sections = 24;
        int airRuntimeId = FALLBACK_AIR;
        int stoneRuntimeId = FALLBACK_STONE;
        if (session != null) {
            sections = Math.max(1, session.bedrockDimensionHeight() >> 4);
            airRuntimeId = session.airRuntimeId();
            stoneRuntimeId = session.stoneRuntimeId();
        }
        ByteBuf payload = encodeColumnPayload(runtimeSections, sections, airRuntimeId, stoneRuntimeId);
        if (payload == null) {
            return null;
        }
        String reject = validateColumnPayload(payload, sections);
        if (reject != null) {
            payload.release();
            throw new IllegalStateException("REAL LevelChunk validation failed: " + reject);
        }

        LevelChunkPacket packet = new LevelChunkPacket();
        packet.setDimension(session != null ? session.bedrockDimensionId() : 0);
        packet.setChunkX(chunkX);
        packet.setChunkZ(chunkZ);
        packet.setSubChunksLength(sections);
        packet.setData(payload);
        packet.setCachingEnabled(false);
        packet.setRequestSubChunks(false);
        return packet;
    }

    /**
     * Encode column payload only (subchunks + biomes + border). Used by unit tests and validation.
     */
    static ByteBuf encodeColumnPayload(int[][] runtimeSections, int sections) {
        return encodeColumnPayload(runtimeSections, sections, FALLBACK_AIR, FALLBACK_STONE);
    }

    static ByteBuf encodeColumnPayload(int[][] runtimeSections, int sections,
                                       int airRuntimeId, int stoneRuntimeId) {
        if (runtimeSections == null || sections < 1) {
            return null;
        }
        ByteBuf payload = Unpooled.buffer(sections * 128 + 64);
        for (int i = 0; i < sections; i++) {
            int[] sec = i < runtimeSections.length ? runtimeSections[i] : null;
            if (sec == null || sec.length < SECTION_BLOCKS) {
                writeUniformSubChunk(payload, airRuntimeId);
            } else {
                writeSectionSubChunk(payload, sec, stoneRuntimeId);
            }
        }
        // Biomes: one singleton + (sections-1)× reuse marker — same as Geyser empty path markers.
        writeUniformBiomeStorage(payload, BIOME_PLAINS);
        for (int i = 1; i < sections; i++) {
            payload.writeByte(0xFF);
        }
        payload.writeByte(0); // border blocks
        return payload;
    }

    /**
     * Reject crashy payloads before they hit the client. Returns null when OK.
     */
    static String validateColumnPayload(ByteBuf payload, int expectedSections) {
        if (payload == null) {
            return "null payload";
        }
        int n = payload.readableBytes();
        if (n < MIN_COLUMN_PAYLOAD_BYTES) {
            return "payload too small (" + n + ")";
        }
        if (n > MAX_COLUMN_PAYLOAD_BYTES) {
            return "payload too large (" + n + ")";
        }
        if (expectedSections < 1 || expectedSections > 64) {
            return "bad section count " + expectedSections;
        }
        int idx = payload.readerIndex();
        try {
            // First subchunk must be v8 + 1 storage + known-good network header.
            if (payload.readableBytes() < 3) {
                return "truncated first subchunk";
            }
            int ver = payload.readUnsignedByte();
            int storages = payload.readUnsignedByte();
            int header = payload.readUnsignedByte();
            if (ver != 8) {
                return "subchunk version=" + ver + " want 8";
            }
            if (storages != 1) {
                return "storageCount=" + storages + " want 1";
            }
            // Network bit: low bit set. bits = header>>1 must be 0,1,2,4,8 (Bedrock skips 3,5,6,7).
            if ((header & 1) == 0) {
                return "network bit unset header=0x" + Integer.toHexString(header);
            }
            int bits = header >> 1;
            if (bits != 0 && bits != 1 && bits != 2 && bits != 4 && bits != 8) {
                return "illegal bits=" + bits + " header=0x" + Integer.toHexString(header);
            }
            // Biome tail: last byte border=0; preceding (sections-1) should be 0xFF reuse when
            // we wrote singleton+reuse (best-effort — only check border).
            int end = payload.writerIndex();
            if (payload.getByte(end - 1) != 0) {
                return "border byte != 0";
            }
            return null;
        } finally {
            payload.readerIndex(idx);
        }
    }

    /** Hex prefix of payload for forensics (does not consume). */
    static String hexPrefix(ByteBuf payload, int maxBytes) {
        if (payload == null) {
            return "";
        }
        int n = Math.min(maxBytes, payload.readableBytes());
        StringBuilder sb = new StringBuilder(n * 2);
        int idx = payload.readerIndex();
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02x", payload.getUnsignedByte(idx + i)));
        }
        return sb.toString();
    }

    /** SubChunk v8 + 1 layer, single-value palette (network short form). */
    static void writeUniformSubChunk(ByteBuf out, int runtimeStateId) {
        out.writeByte(8); // version
        out.writeByte(1); // storage count
        out.writeByte(1); // bits=0 | network → (0<<1)|1
        writeSignedVarInt(out, runtimeStateId);
    }

    static void writeSectionSubChunk(ByteBuf out, int[] states4096Yzx) {
        writeSectionSubChunk(out, states4096Yzx, FALLBACK_STONE);
    }

    static void writeSectionSubChunk(ByteBuf out, int[] states4096Yzx, int stoneRuntimeId) {
        // Remap JE YZX → Bedrock XZY while building palette (Geyser indexYZXtoXZY).
        int[] palette = new int[256];
        int paletteSize = 0;
        int[] indices = new int[SECTION_BLOCKS];
        for (int yzx = 0; yzx < SECTION_BLOCKS; yzx++) {
            int xzy = indexYzxToXzy(yzx);
            int state = states4096Yzx[yzx];
            int pal = -1;
            for (int p = 0; p < paletteSize; p++) {
                if (palette[p] == state) {
                    pal = p;
                    break;
                }
            }
            if (pal < 0) {
                if (paletteSize >= palette.length) {
                    // Too many unique — stone uniform (safe, non-crashy).
                    writeUniformSubChunk(out, stoneRuntimeId);
                    return;
                }
                pal = paletteSize;
                palette[paletteSize++] = state;
            }
            indices[xzy] = pal;
        }
        if (paletteSize == 1) {
            writeUniformSubChunk(out, palette[0]);
            return;
        }

        int bits = bitsForPalette(paletteSize);
        out.writeByte(8);
        out.writeByte(1);
        out.writeByte((bits << 1) | 1); // network bit-array header
        int blocksPerWord = Math.max(1, 32 / bits);
        int words = (SECTION_BLOCKS + blocksPerWord - 1) / blocksPerWord;
        int mask = (1 << bits) - 1;
        for (int w = 0; w < words; w++) {
            int word = 0;
            for (int b = 0; b < blocksPerWord; b++) {
                int idx = w * blocksPerWord + b;
                if (idx >= SECTION_BLOCKS) {
                    break;
                }
                word |= (indices[idx] & mask) << (b * bits);
            }
            out.writeIntLE(word);
        }
        writeSignedVarInt(out, paletteSize);
        for (int p = 0; p < paletteSize; p++) {
            writeSignedVarInt(out, palette[p]);
        }
    }

    /**
     * JE section index {@code y<<8 | z<<4 | x} → Bedrock {@code x<<8 | z<<4 | y}.
     * Same as Geyser {@code ChunkUtils.indexYZXtoXZY}.
     */
    static int indexYzxToXzy(int yzx) {
        return (yzx >> 8) | (yzx & 0xF0) | ((yzx & 0xF) << 8);
    }

    /** Bedrock skips bit sizes 3,5,6,7 — clamp like chassis {@code BedrockWorldCodec}. */
    static int bitsForPalette(int paletteSize) {
        int bits = 1;
        while ((1 << bits) < paletteSize && bits < 8) {
            bits++;
        }
        if (bits == 3) {
            bits = 4;
        }
        if (bits > 4 && bits < 8) {
            bits = 8;
        }
        return bits;
    }

    /**
     * Biome singleton storage — matches Geyser {@code EMPTY_BIOME_DATA} shape:
     * {@code 0x01} network header + zigzag biome id (no extra pad byte).
     */
    static void writeUniformBiomeStorage(ByteBuf out, int biomeId) {
        out.writeByte(1); // (0<<1)|1
        writeSignedVarInt(out, Math.max(0, biomeId));
    }

    static void writeUnsignedVarInt(ByteBuf out, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    static void writeSignedVarInt(ByteBuf out, int value) {
        writeUnsignedVarInt(out, (value << 1) ^ (value >> 31));
    }

    /**
     * Count non-air runtime blocks in a box around world feet — used to gate PLAYER_SPAWN
     * so Bedrock does not enable physics over a void / all-air remap.
     *
     * @param worldMinY dimension min Y (overworld {@code -64})
     * @param blockX    floored feet X
     * @param feetY     exact feet Y
     * @param blockZ    floored feet Z
     */
    public static int countNonAirNearFeet(
            int[][] runtimeSections, int worldMinY, int blockX, double feetY, int blockZ) {
        return countNonAirNearFeet(runtimeSections, worldMinY, blockX, feetY, blockZ, FALLBACK_AIR);
    }

    public static int countNonAirNearFeet(
            int[][] runtimeSections, int worldMinY, int blockX, double feetY, int blockZ,
            int airRuntimeId) {
        if (runtimeSections == null) {
            return 0;
        }
        int feetBlockY = (int) Math.floor(feetY);
        // Prefer the block under feet + a few below / one above (standing / buried / thin floor).
        int y0 = feetBlockY - 3;
        int y1 = feetBlockY + 1;
        int localX = blockX & 15;
        int localZ = blockZ & 15;
        int solid = 0;
        for (int y = y0; y <= y1; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int wx = localX + dx;
                    int wz = localZ + dz;
                    if (wx < 0 || wx > 15 || wz < 0 || wz > 15) {
                        continue;
                    }
                    if (isNonAirAtLocal(runtimeSections, worldMinY, wx, y, wz, airRuntimeId)) {
                        solid++;
                    }
                }
            }
        }
        return solid;
    }

    /**
     * JE feet standing ON TOP of a solid (feetY = solidTop = blockY+1). Lift when remapped
     * column has non-air at feet/head, or a tight ceiling at feet+2/+3 (2-high pocket /
     * mesh mismatch → black screen), scanning up to +96 for the first open air gap.
     * If the column is all solid in range, force {@code floor(feetY)+1}.
     */
    public static double resolveStandOnFeetY(
            int[][] runtimeSections, int worldMinY, int blockX, double feetY, int blockZ) {
        return resolveStandOnFeetY(runtimeSections, worldMinY, blockX, feetY, blockZ, FALLBACK_AIR);
    }

    public static double resolveStandOnFeetY(
            int[][] runtimeSections, int worldMinY, int blockX, double feetY, int blockZ,
            int airRuntimeId) {
        if (runtimeSections == null || Double.isNaN(feetY)) {
            return feetY;
        }
        int feetBlockY = (int) Math.floor(feetY);
        boolean feetSolid = isNonAirAt(runtimeSections, worldMinY, blockX, feetBlockY, blockZ, airRuntimeId);
        boolean headSolid = isNonAirAt(runtimeSections, worldMinY, blockX, feetBlockY + 1, blockZ, airRuntimeId);
        boolean tightCeiling = isNonAirAt(runtimeSections, worldMinY, blockX, feetBlockY + 2, blockZ, airRuntimeId)
                || isNonAirAt(runtimeSections, worldMinY, blockX, feetBlockY + 3, blockZ, airRuntimeId);
        if (!feetSolid && !headSolid && !tightCeiling) {
            return feetY;
        }
        int columnMaxY = worldMinY + runtimeSections.length * 16 - 2;
        int scanMax = Math.min(columnMaxY, feetBlockY + 96);
        // Prefer a 3-high open gap (feet+head+above) so eye is not under a tight ceiling.
        for (int y = feetBlockY; y <= scanMax; y++) {
            if (!isNonAirAt(runtimeSections, worldMinY, blockX, y, blockZ, airRuntimeId)
                    && !isNonAirAt(runtimeSections, worldMinY, blockX, y + 1, blockZ, airRuntimeId)
                    && !isNonAirAt(runtimeSections, worldMinY, blockX, y + 2, blockZ, airRuntimeId)) {
                return y + 0.0;
            }
        }
        // Fall back to any 2-high gap.
        for (int y = feetBlockY; y <= scanMax; y++) {
            if (!isNonAirAt(runtimeSections, worldMinY, blockX, y, blockZ, airRuntimeId)
                    && !isNonAirAt(runtimeSections, worldMinY, blockX, y + 1, blockZ, airRuntimeId)) {
                return y + 0.0;
            }
        }
        // Mapper column all solid / no usable gap — force FoliaY+1 so eye clears floor(feet).
        return feetBlockY + 1.0;
    }

    /** World-space non-air probe (JE YZX section index). */
    public static boolean isNonAirAt(
            int[][] runtimeSections, int worldMinY, int blockX, int blockY, int blockZ) {
        return isNonAirAt(runtimeSections, worldMinY, blockX, blockY, blockZ, FALLBACK_AIR);
    }

    public static boolean isNonAirAt(
            int[][] runtimeSections, int worldMinY, int blockX, int blockY, int blockZ,
            int airRuntimeId) {
        if (runtimeSections == null) {
            return false;
        }
        return isNonAirAtLocal(runtimeSections, worldMinY, blockX & 15, blockY, blockZ & 15, airRuntimeId);
    }

    private static boolean isNonAirAtLocal(
            int[][] runtimeSections, int worldMinY, int localX, int blockY, int localZ) {
        return isNonAirAtLocal(runtimeSections, worldMinY, localX, blockY, localZ, FALLBACK_AIR);
    }

    private static boolean isNonAirAtLocal(
            int[][] runtimeSections, int worldMinY, int localX, int blockY, int localZ,
            int airRuntimeId) {
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            return false;
        }
        int sectionIndex = (blockY - worldMinY) >> 4;
        if (sectionIndex < 0 || sectionIndex >= runtimeSections.length) {
            return false;
        }
        int[] sec = runtimeSections[sectionIndex];
        if (sec == null || sec.length < SECTION_BLOCKS) {
            return false;
        }
        int ly = blockY & 15;
        int yzx = (ly << 8) | (localZ << 4) | localX;
        return sec[yzx] != airRuntimeId;
    }

    /** Total non-air blocks in the column (forensics). */
    public static int countNonAirColumn(int[][] runtimeSections) {
        return countNonAirColumn(runtimeSections, FALLBACK_AIR);
    }

    public static int countNonAirColumn(int[][] runtimeSections, int airRuntimeId) {
        if (runtimeSections == null) {
            return 0;
        }
        int n = 0;
        for (int[] sec : runtimeSections) {
            if (sec == null) {
                continue;
            }
            for (int state : sec) {
                if (state != airRuntimeId) {
                    n++;
                }
            }
        }
        return n;
    }
}

package com.yapcore.crossplay.bedrock.codec;

import com.yapcore.crossplay.bedrock.BedrockAvailableCommands;
import com.yapcore.crossplay.bedrock.BedrockItemStates;
import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.bedrock.BedrockPacketIds;
import com.yapcore.crossplay.bedrock.BedrockPaperRecipes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import static com.yapcore.crossplay.bedrock.codec.BedrockCodecBinary.*;

public final class BedrockWorldCodec {
    private BedrockWorldCodec() {}
    /**
     * Empty column marker (sub_chunk_count=0) — known-good for smoke / join.
     * Use {@link #levelChunkFlat} when the client accepts real palettes.
     */
    public static ByteBuf levelChunkEmpty(int chunkX, int chunkZ) {
        return levelChunkMarker(chunkX, chunkZ);
    }

    public static ByteBuf levelChunkMarker(int chunkX, int chunkZ) {
        ByteBuf out = Unpooled.buffer(64);
        writeUnsignedVarInt(out, BedrockPacketIds.LEVEL_CHUNK.id);
        writeZigZag32(out, chunkX);
        writeZigZag32(out, chunkZ);
        writeZigZag32(out, 0);
        writeUnsignedVarInt(out, 0);
        out.writeBoolean(false);
        writeUnsignedVarInt(out, 0);
        return out;
    }

    /** Air / dirt / stone / grass_block / bedrock defaultState (prismarine-registry bedrock_1.21.50). */
    static final int STATE_AIR = 11261;
    static final int STATE_DIRT = 8805;
    static final int STATE_STONE = 2325;
    static final int STATE_GRASS = 9981;
    static final int STATE_BEDROCK = 11785;
    private static final int BIOME_PLAINS = 1;
    private static final int SHIELD_NETWORK_ID = 1162;

    public static int hashedAir() {
        return STATE_AIR;
    }

    public static int hashedDirt() {
        return STATE_DIRT;
    }

    public static int hashedStone() {
        return STATE_STONE;
    }

    public static int hashedGrass() {
        return STATE_GRASS;
    }

    public static int hashedBedrock() {
        return STATE_BEDROCK;
    }

    public static ByteBuf levelChunkFlat(int chunkX, int chunkZ) {
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
        out.writeBoolean(false); // cache_enabled
        writeUnsignedVarInt(out, payload.readableBytes());
        out.writeBytes(payload);
        payload.release();
        return out;
    }

    /**
     * Encode a Paper (or other) column: {@code states[section][4096]} hashed runtime ids,
     * section 0 = y −64..−49, XZY index {@code (x<<8)|(z<<4)|localY}.
     */
    public static ByteBuf levelChunkFromColumn(int chunkX, int chunkZ, int[][] states) {
        if (states == null || states.length == 0) {
            return levelChunkFlat(chunkX, chunkZ);
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

        ByteBuf out = Unpooled.buffer(payload.readableBytes() + 32);
        writeUnsignedVarInt(out, BedrockPacketIds.LEVEL_CHUNK.id);
        writeZigZag32(out, chunkX);
        writeZigZag32(out, chunkZ);
        writeZigZag32(out, 0);
        writeUnsignedVarInt(out, sections);
        out.writeBoolean(false);
        writeUnsignedVarInt(out, payload.readableBytes());
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
    public static ByteBuf updateBlock(int x, int y, int z, int runtimeId, int flags, int layer) {
        ByteBuf out = Unpooled.buffer(32);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_UPDATE_BLOCK);
        writeBlockPosition(out, x, y, z);
        writeUnsignedVarInt(out, runtimeId);
        writeUnsignedVarInt(out, flags);
        writeUnsignedVarInt(out, layer);
        return out;
    }

    /** AVAILABLE_ENTITY_IDENTIFIERS — empty network NBT compound. */
    public static ByteBuf availableEntityIdentifiersEmpty() {
        ByteBuf out = Unpooled.buffer(16);
        writeUnsignedVarInt(out, BedrockPacketIds.AVAILABLE_ACTOR_IDENTIFIERS.id);
        writeEmptyNetworkNbt(out);
        return out;
    }

    /** BIOME_DEFINITION_LIST — empty network NBT compound. */
    public static ByteBuf biomeDefinitionListEmpty() {
        ByteBuf out = Unpooled.buffer(16);
        writeUnsignedVarInt(out, BedrockPacketIds.BIOME_DEFINITION_LIST.id);
        writeEmptyNetworkNbt(out);
        return out;
    }
    public static ByteBuf setTime(int time) {
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, BedrockPacketIds.SET_TIME.id);
        writeSignedVarInt(out, time);
        return out;
    }

    public static ByteBuf setDifficulty(int difficulty) {
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, BedrockPacketIds.SET_DIFFICULTY.id);
        writeUnsignedVarInt(out, difficulty);
        return out;
    }

    public static ByteBuf setCommandsEnabled(boolean enabled) {
        ByteBuf out = Unpooled.buffer(4);
        writeUnsignedVarInt(out, 0x3b); // SET_COMMANDS_ENABLED
        out.writeBoolean(enabled);
        return out;
    }

    public static BedrockPacketCodec.PlayerActionDecode tryDecodePlayerAction(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            long entityId = readUnsignedVarInt(body);
            int action = readUnsignedVarInt(body);
            int[] pos = readBlockPosition(body);
            int resultFace = readUnsignedVarInt(body);
            return new BedrockPacketCodec.PlayerActionDecode(entityId, action, pos[0], pos[1], pos[2], resultFace);
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }
    public static BedrockPacketCodec.InventoryTxDecode tryDecodeInventoryTransaction(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            int requestId = 0;
            if (body.isReadable()) {
                // Best-effort: legacy transaction type is unsigned varint first on many builds
                int txType = readUnsignedVarInt(body);
                // Use item data may follow — capture type for PLACE vs BREAK heuristics
                int[] pos = null;
                if (body.readableBytes() >= 6) {
                    try {
                        pos = readBlockPosition(body);
                    } catch (Exception ignored) {
                        pos = null;
                    }
                }
                return new BedrockPacketCodec.InventoryTxDecode(txType, requestId,
                        pos != null ? pos[0] : 0,
                        pos != null ? pos[1] : 0,
                        pos != null ? pos[2] : 0,
                        pos != null);
            }
            return null;
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }
    public static BedrockPacketCodec.AuthInputDecode tryDecodeAuthInput(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            // Modern PLAYER_AUTH_INPUT often starts with Vec3 position as floats
            float x = body.readFloatLE();
            float y = body.readFloatLE();
            float z = body.readFloatLE();
            float pitch = body.readableBytes() >= 4 ? body.readFloatLE() : 0f;
            float yaw = body.readableBytes() >= 4 ? body.readFloatLE() : 0f;
            float headYaw = body.readableBytes() >= 4 ? body.readFloatLE() : yaw;
            long tick = 0;
            if (body.readableBytes() >= 1) {
                try {
                    // input flags varint / bitset — skip best-effort then tick
                    readUnsignedVarInt(body); // input data
                    if (body.readableBytes() >= 1) {
                        readUnsignedVarInt(body); // input mode
                    }
                    if (body.readableBytes() >= 1) {
                        readUnsignedVarInt(body); // play mode
                    }
                    if (body.readableBytes() >= 1) {
                        readUnsignedVarInt(body); // interaction model
                    }
                    if (body.readableBytes() >= 8) {
                        tick = body.readLongLE();
                    }
                } catch (Exception ignored) {
                    // position alone is enough for MOVE
                }
            }
            return new BedrockPacketCodec.AuthInputDecode(x, y, z, pitch, yaw, headYaw, tick);
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }
    public static BedrockPacketCodec.InteractDecode tryDecodeInteract(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            byte action = body.readByte();
            long targetRuntimeId = readUnsignedVarInt(body);
            return new BedrockPacketCodec.InteractDecode(action, targetRuntimeId);
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }
    public static BedrockPacketCodec.MoveDecode tryDecodeMove(ByteBuf body) {
        try {
            int runtimeId = readUnsignedVarInt(body);
            float x = body.readFloatLE();
            float y = body.readFloatLE();
            float z = body.readFloatLE();
            float pitch = body.readFloatLE();
            float yaw = body.readFloatLE();
            return new BedrockPacketCodec.MoveDecode(runtimeId, x, y, z, pitch, yaw);
        } catch (Exception e) {
            return null;
        }
    }
    public static ByteBuf movePlayer(long runtimeId, float x, float y, float z,
                                     float pitch, float yaw, float headYaw, byte mode, boolean onGround) {
        ByteBuf out = Unpooled.buffer(48);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_MOVE_PLAYER);
        writeUnsignedVarInt(out, (int) runtimeId);
        out.writeFloatLE(x);
        out.writeFloatLE(y);
        out.writeFloatLE(z);
        out.writeFloatLE(pitch);
        out.writeFloatLE(yaw);
        out.writeFloatLE(headYaw);
        out.writeByte(mode);
        out.writeBoolean(onGround);
        writeUnsignedVarInt(out, 0); // riding eid
        out.writeIntLE(0); // tick
        return out;
    }
}

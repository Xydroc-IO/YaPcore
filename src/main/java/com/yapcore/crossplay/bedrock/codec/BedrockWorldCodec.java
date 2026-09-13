package com.yapcore.crossplay.bedrock.codec;

import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.bedrock.BedrockPacketIds;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import static com.yapcore.crossplay.bedrock.codec.BedrockCodecBinary.*;

public final class BedrockWorldCodec {
    private BedrockWorldCodec() {}
    /**
     * Empty column marker (sub_chunk_count=0) — known-good for smoke / join.
     * Use {@link #levelChunkFlat} when the client accepts real palettes.
     */
    /**
     * Cloudburst {@code LevelChunkSerializer_v2168} cutover (1.26.40+).
     * Proto 2207 is unknown upstream but closest codecs are 2168/2169/2192 — all use v2168.
     * Below this: v649/v486 layout (count → cache → blobs-if-cache → payload).
     */
    public static final int LEVEL_CHUNK_V2168_PROTOCOL = 2168;

    public static boolean usesLevelChunkV2168(int protocol) {
        return protocol >= LEVEL_CHUNK_V2168_PROTOCOL;
    }

    public static ByteBuf levelChunkEmpty(int chunkX, int chunkZ) {
        return BedrockWorldChunkCodec.levelChunkEmpty(chunkX, chunkZ);
    }

    public static ByteBuf levelChunkEmpty(int chunkX, int chunkZ, int protocol) {
        return BedrockWorldChunkCodec.levelChunkEmpty(chunkX, chunkZ, protocol);
    }

    public static ByteBuf levelChunkMarker(int chunkX, int chunkZ) {
        return BedrockWorldChunkCodec.levelChunkMarker(chunkX, chunkZ);
    }

    public static ByteBuf levelChunkMarker(int chunkX, int chunkZ, int protocol) {
        return BedrockWorldChunkCodec.levelChunkMarker(chunkX, chunkZ, protocol);
    }

    /** Air / dirt / stone / grass_block / bedrock defaultState (prismarine-registry bedrock_1.21.50). */
    static final int STATE_AIR = BedrockWorldChunkCodec.STATE_AIR;
    static final int STATE_DIRT = BedrockWorldChunkCodec.STATE_DIRT;
    static final int STATE_STONE = BedrockWorldChunkCodec.STATE_STONE;
    static final int STATE_GRASS = BedrockWorldChunkCodec.STATE_GRASS;
    static final int STATE_BEDROCK = BedrockWorldChunkCodec.STATE_BEDROCK;

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
        return BedrockWorldChunkCodec.levelChunkFlat(chunkX, chunkZ);
    }

    /** Cloudburst-aligned: v2168 header when {@code protocol >= 2168}. */
    public static ByteBuf levelChunkFlat(int chunkX, int chunkZ, int protocol) {
        return BedrockWorldChunkCodec.levelChunkFlat(chunkX, chunkZ, protocol);
    }

    /**
     * Pre-v2168 LevelChunk header (Cloudburst v486/v649 through ~1001):
     * count → cache_enabled → (blobs if cache) → payload.
     */
    public static ByteBuf levelChunkFlatLegacy(int chunkX, int chunkZ) {
        return BedrockWorldChunkCodec.levelChunkFlatLegacy(chunkX, chunkZ);
    }

    /**
     * Encode a Paper (or other) column: {@code states[section][4096]} hashed runtime ids,
     * section 0 = y −64..−49, XZY index {@code (x<<8)|(z<<4)|localY}.
     */
    public static ByteBuf levelChunkFromColumn(int chunkX, int chunkZ, int[][] states) {
        return BedrockWorldChunkCodec.levelChunkFromColumn(chunkX, chunkZ, states);
    }

    public static ByteBuf levelChunkFromColumn(int chunkX, int chunkZ, int[][] states, int protocol) {
        return BedrockWorldChunkCodec.levelChunkFromColumn(chunkX, chunkZ, states, protocol);
    }

    /**
     * Hashed-column payload only (subchunks + biomes + border) for Cloudburst
     * {@code LevelChunkPacket#setData}. Matches StartGame {@code blockNetworkIdsHashed}.
     */
    public static ByteBuf hashedColumnPayload(int[][] states) {
        return BedrockWorldChunkCodec.hashedColumnPayload(states);
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

    /**
     * BIOME_DEFINITION_LIST for proto ≥776: BiomeDefinitions[] + StringList[]
     * (NBT compound format was removed — sending old dumps crashes modern clients).
     */
    public static ByteBuf biomeDefinitionListEmpty() {
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, BedrockPacketIds.BIOME_DEFINITION_LIST.id);
        writeUnsignedVarInt(out, 0); // biome definitions
        writeUnsignedVarInt(out, 0); // string dictionary
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

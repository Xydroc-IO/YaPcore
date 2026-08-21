package com.yapcore.crossplay.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Bedrock gameplay packet codecs (IDs aligned with modern BE). Clean-room 4.G1.
 */
public final class BedrockPacketCodec {

    public static final int ID_LOGIN = 0x01;
    public static final int ID_PLAY_STATUS = 0x02;
    public static final int ID_SERVER_TO_CLIENT_HANDSHAKE = 0x03;
    public static final int ID_CLIENT_TO_SERVER_HANDSHAKE = 0x04;
    public static final int ID_DISCONNECT = 0x05;
    public static final int ID_RESOURCE_PACKS_INFO = 0x06;
    public static final int ID_RESOURCE_PACK_STACK = 0x07;
    public static final int ID_RESOURCE_PACK_CLIENT_RESPONSE = 0x08;
    public static final int ID_TEXT = 0x09;
    public static final int ID_START_GAME = 0x0b;
    public static final int ID_ADD_PLAYER = 0x0c;
    public static final int ID_REMOVE_ENTITY = 0x0e;
    public static final int ID_MOVE_PLAYER = 0x13;
    public static final int ID_PLAYER_ACTION = 0x24;
    public static final int ID_INTERACT = 0x21;
    public static final int ID_CONTAINER_OPEN = 0x2e;
    public static final int ID_CONTAINER_CLOSE = 0x2f;
    public static final int ID_INVENTORY_CONTENT = 0x31;
    public static final int ID_MOB_EQUIPMENT = 0x1f;
    public static final int ID_LEVEL_EVENT = 0x19;
    public static final int ID_UPDATE_BLOCK = 0x15;
    public static final int ID_UPDATE_ATTRIBUTES = 0x1d;
    public static final int ID_SET_ENTITY_DATA = 0x27;
    public static final int ID_RESPAWN = 0x2d;
    public static final int ID_MODAL_FORM_REQUEST = 0x64;
    public static final int ID_MODAL_FORM_RESPONSE = 0x65;
    public static final int ID_PLAYER_SKIN = 0x5d;
    public static final int ID_NETWORK_SETTINGS = 0x8f;
    public static final int ID_REQUEST_NETWORK_SETTINGS = 0xc1;

    public enum PlayStatus {
        LOGIN_SUCCESS(0),
        LOGIN_FAILED_CLIENT(1),
        LOGIN_FAILED_SERVER(2),
        PLAYER_SPAWN(3),
        LOGIN_FAILED_INVALID_TENANT(4),
        LOGIN_FAILED_VANILLA_EDU(5),
        LOGIN_FAILED_EDU_VANILLA(6),
        LOGIN_FAILED_SERVER_FULL(7);

        public final int code;

        PlayStatus(int code) {
            this.code = code;
        }
    }

    private BedrockPacketCodec() {
    }

    public static void writeUnsignedVarInt(ByteBuf out, int value) {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    public static int readUnsignedVarInt(ByteBuf in) {
        int value = 0;
        int size = 0;
        int b;
        while (((b = in.readUnsignedByte()) & 0x80) == 0x80) {
            value |= (b & 0x7F) << (size++ * 7);
            if (size > 5) {
                throw new IllegalArgumentException("VarInt too big");
            }
        }
        return value | ((b & 0x7F) << (size * 7));
    }

    public static void writeString(ByteBuf out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeUnsignedVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    public static String readString(ByteBuf in) {
        int len = readUnsignedVarInt(in);
        byte[] bytes = new byte[Math.min(len, in.readableBytes())];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static ByteBuf playStatus(PlayStatus status) {
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, ID_PLAY_STATUS);
        out.writeInt(status.code);
        return out;
    }

    public static ByteBuf textChat(String source, String message) {
        ByteBuf out = Unpooled.buffer(64 + message.length());
        writeUnsignedVarInt(out, ID_TEXT);
        out.writeByte(1); // chat
        out.writeBoolean(false); // needs translation
        writeString(out, source);
        writeString(out, message);
        writeString(out, ""); // xuid
        writeString(out, ""); // platform
        return out;
    }

    public static ByteBuf movePlayer(long runtimeId, float x, float y, float z,
                                     float pitch, float yaw, float headYaw, byte mode, boolean onGround) {
        ByteBuf out = Unpooled.buffer(48);
        writeUnsignedVarInt(out, ID_MOVE_PLAYER);
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

    /** Minimal StartGame — enough for spawn scaffolding; fields deepen toward full Geyser parity. */
    public static ByteBuf startGame(long entityUniqueId, long runtimeId, String levelName,
                                    int blockX, int blockY, int blockZ, UUID worldId) {
        ByteBuf out = Unpooled.buffer(256);
        writeUnsignedVarInt(out, ID_START_GAME);
        out.writeLongLE(entityUniqueId);
        writeUnsignedVarInt(out, (int) runtimeId);
        writeUnsignedVarInt(out, 0); // player gamemode
        out.writeFloatLE(blockX + 0.5f);
        out.writeFloatLE(blockY + 1.62f);
        out.writeFloatLE(blockZ + 0.5f);
        out.writeFloatLE(0f); // pitch
        out.writeFloatLE(0f); // yaw
        // seed / biome / dimension stubs
        out.writeLongLE(worldId.getMostSignificantBits() ^ worldId.getLeastSignificantBits());
        writeUnsignedVarInt(out, 0); // spawn biome type
        writeString(out, "plains");
        writeUnsignedVarInt(out, 0); // dimension
        writeUnsignedVarInt(out, 1); // generator
        writeUnsignedVarInt(out, 0); // world gamemode
        writeUnsignedVarInt(out, 0); // difficulty
        out.writeIntLE(blockX);
        out.writeIntLE(blockY);
        out.writeIntLE(blockZ);
        out.writeBoolean(false); // achievements
        writeUnsignedVarInt(out, 0); // editor world type
        out.writeBoolean(false); // created in editor
        out.writeBoolean(false); // exported from editor
        writeUnsignedVarInt(out, 0); // day cycle stop time
        out.writeIntLE(0); // edu offer
        out.writeBoolean(false); // edu features
        writeString(out, ""); // edu product id
        out.writeFloatLE(0f); // rain
        out.writeFloatLE(0f); // lightning
        writeString(out, ""); // multiplayer authority
        out.writeBoolean(true); // multiplayer game
        out.writeBoolean(true); // lan broadcast
        writeUnsignedVarInt(out, 0); // xbl broadcast
        writeUnsignedVarInt(out, 0); // platform broadcast
        out.writeBoolean(true); // commands enabled
        out.writeBoolean(false); // texture packs required
        writeUnsignedVarInt(out, 0); // gamerules count
        writeUnsignedVarInt(out, 0); // experiments
        out.writeBoolean(false); // experiments previously toggled
        out.writeBoolean(false); // bonus chest
        out.writeBoolean(false); // map enabled
        out.writeByte(1); // permission level
        out.writeIntLE(4); // chunk tick range
        out.writeBoolean(false); // locked behavior
        out.writeBoolean(false); // locked resource
        out.writeBoolean(false); // from locked template
        out.writeBoolean(false); // msa gamertags only
        out.writeBoolean(false); // from world template
        out.writeBoolean(false); // world template option locked
        out.writeBoolean(false); // only spawn v1 villagers
        writeString(out, "1.21.0"); // persona disabled / base game version
        out.writeIntLE(0); // limited world width
        out.writeIntLE(0); // limited world length
        out.writeBoolean(false); // nether type
        writeString(out, ""); // edu resource uri button
        writeString(out, ""); // edu resource uri link
        out.writeBoolean(false); // experimental gameplay
        out.writeByte(0); // chat restriction
        out.writeBoolean(false); // disable player interactions
        writeString(out, levelName);
        writeString(out, levelName);
        writeString(out, ""); // premium world template
        out.writeBoolean(false); // is trial
        writeUnsignedVarInt(out, 0); // rewind history size / movement authority combo (stub)
        out.writeBoolean(false); // server authoritative block breaking
        out.writeLongLE(System.currentTimeMillis());
        out.writeIntLE(0); // enchant seed
        writeUnsignedVarInt(out, 0); // block properties
        writeUnsignedVarInt(out, 0); // item states
        writeString(out, ""); // multiplayer correlation id
        out.writeBoolean(false); // inventories server authoritative
        writeString(out, ""); // server engine version
        return out;
    }

    public static ByteBuf resourcePacksInfoEmpty() {
        ByteBuf out = Unpooled.buffer(16);
        writeUnsignedVarInt(out, ID_RESOURCE_PACKS_INFO);
        out.writeBoolean(false); // forced to accept
        out.writeBoolean(false); // scripting
        out.writeBoolean(false); // forcing server packs
        writeUnsignedVarInt(out, 0); // behavior packs
        writeUnsignedVarInt(out, 0); // resource packs
        return out;
    }

    public static ByteBuf resourcePackStackEmpty() {
        ByteBuf out = Unpooled.buffer(16);
        writeUnsignedVarInt(out, ID_RESOURCE_PACK_STACK);
        out.writeBoolean(false); // must accept
        writeUnsignedVarInt(out, 0); // behavior
        writeUnsignedVarInt(out, 0); // resource
        writeString(out, "1.21.0");
        out.writeBoolean(false); // experiments
        out.writeBoolean(false); // previously toggled
        return out;
    }

    public static ByteBuf modalFormRequest(int formId, String json) {
        ByteBuf out = Unpooled.buffer(16 + json.length());
        writeUnsignedVarInt(out, ID_MODAL_FORM_REQUEST);
        writeUnsignedVarInt(out, formId);
        writeString(out, json);
        return out;
    }

    public static ByteBuf playerSkin(UUID uuid, String skinId, String skinDataBase64, String capeData, String geometry) {
        ByteBuf out = Unpooled.buffer(128);
        writeUnsignedVarInt(out, ID_PLAYER_SKIN);
        out.writeLongLE(uuid.getMostSignificantBits());
        out.writeLongLE(uuid.getLeastSignificantBits());
        writeString(out, skinId);
        writeString(out, skinDataBase64 == null ? "" : skinDataBase64);
        writeString(out, capeData == null ? "" : capeData);
        writeString(out, geometry == null ? "geometry.humanoid.custom" : geometry);
        return out;
    }

    public static ByteBuf networkSettings(int compressionThreshold, int compressionAlgorithm,
                                          boolean clientThrottle, float clientThrottleThreshold,
                                          float clientThrottleScalar) {
        ByteBuf out = Unpooled.buffer(16);
        writeUnsignedVarInt(out, ID_NETWORK_SETTINGS);
        out.writeShortLE(compressionThreshold);
        out.writeShortLE(compressionAlgorithm);
        out.writeBoolean(clientThrottle);
        out.writeFloatLE(clientThrottleThreshold);
        out.writeFloatLE(clientThrottleScalar);
        return out;
    }

    /** No compression yet — threshold 0 tells client to send uncompressed. */
    public static ByteBuf networkSettingsUncompressed() {
        return networkSettings(0, 0, false, 0f, 0f);
    }

    public static ByteBuf chunkRadiusUpdated(int radius) {
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, BedrockPacketIds.CHUNK_RADIUS_UPDATED.id);
        writeUnsignedVarInt(out, radius);
        return out;
    }

    public static ByteBuf networkChunkPublisherUpdate(int blockX, int blockY, int blockZ, int radiusBlocks) {
        ByteBuf out = Unpooled.buffer(24);
        writeUnsignedVarInt(out, 0x79); // NETWORK_CHUNK_PUBLISHER_UPDATE
        out.writeIntLE(blockX);
        out.writeIntLE(blockY);
        out.writeIntLE(blockZ);
        writeUnsignedVarInt(out, radiusBlocks);
        return out;
    }

    /**
     * Minimal empty LEVEL_CHUNK (air column) so clients progress past spawn.
     * Full palette codecs deepen later.
     */
    public static ByteBuf levelChunkEmpty(int chunkX, int chunkZ) {
        ByteBuf out = Unpooled.buffer(64);
        writeUnsignedVarInt(out, BedrockPacketIds.LEVEL_CHUNK.id);
        out.writeIntLE(chunkX);
        out.writeIntLE(chunkZ);
        writeUnsignedVarInt(out, 0); // dimension
        writeUnsignedVarInt(out, 1); // subchunk count (empty)
        out.writeBoolean(false); // cache enabled
        writeUnsignedVarInt(out, 0); // blob count
        // payload: empty subchunks marker
        ByteBuf payload = Unpooled.buffer(8);
        payload.writeByte(0); // version / empty
        writeUnsignedVarInt(out, payload.readableBytes());
        out.writeBytes(payload);
        payload.release();
        return out;
    }

    public static ByteBuf updateBlock(int x, int y, int z, int runtimeId, int flags, int layer) {
        ByteBuf out = Unpooled.buffer(32);
        writeUnsignedVarInt(out, ID_UPDATE_BLOCK);
        writeBlockPosition(out, x, y, z);
        writeUnsignedVarInt(out, runtimeId);
        writeUnsignedVarInt(out, flags);
        writeUnsignedVarInt(out, layer);
        return out;
    }

    /** Minimal ADD_PLAYER — enough for other Bedrock clients to see a peer. */
    public static ByteBuf addPlayer(UUID uuid, String username, long runtimeId,
                                    float x, float y, float z, float yaw, float pitch) {
        ByteBuf out = Unpooled.buffer(128 + username.length());
        writeUnsignedVarInt(out, ID_ADD_PLAYER);
        out.writeLongLE(uuid.getMostSignificantBits());
        out.writeLongLE(uuid.getLeastSignificantBits());
        writeString(out, username);
        writeUnsignedVarInt(out, (int) runtimeId);
        out.writeFloatLE(x);
        out.writeFloatLE(y);
        out.writeFloatLE(z);
        out.writeFloatLE(pitch);
        out.writeFloatLE(yaw);
        out.writeFloatLE(yaw); // head yaw
        // held item empty
        writeUnsignedVarInt(out, 0); // network id air
        writeUnsignedVarInt(out, 0); // metadata count
        writeUnsignedVarInt(out, 0); // unique entity links
        writeString(out, ""); // device id
        writeUnsignedVarInt(out, 0); // build platform
        return out;
    }

    public static ByteBuf addActor(long uniqueId, long runtimeId, String actorType,
                                   float x, float y, float z, float yaw, float pitch) {
        ByteBuf out = Unpooled.buffer(96 + actorType.length());
        writeUnsignedVarInt(out, BedrockPacketIds.ADD_ACTOR.id);
        out.writeLongLE(uniqueId);
        writeUnsignedVarInt(out, (int) runtimeId);
        writeString(out, actorType);
        out.writeFloatLE(x);
        out.writeFloatLE(y);
        out.writeFloatLE(z);
        out.writeFloatLE(0f); // vel
        out.writeFloatLE(0f);
        out.writeFloatLE(0f);
        out.writeFloatLE(pitch);
        out.writeFloatLE(yaw);
        out.writeFloatLE(yaw);
        writeUnsignedVarInt(out, 0); // attributes
        writeUnsignedVarInt(out, 0); // metadata
        writeUnsignedVarInt(out, 0); // entity links
        return out;
    }

    public static ByteBuf removeActor(long uniqueEntityId) {
        ByteBuf out = Unpooled.buffer(16);
        writeUnsignedVarInt(out, ID_REMOVE_ENTITY);
        out.writeLongLE(uniqueEntityId);
        return out;
    }

    public static ByteBuf inventoryContentEmpty(int windowId, int size) {
        ByteBuf out = Unpooled.buffer(16 + size);
        writeUnsignedVarInt(out, ID_INVENTORY_CONTENT);
        writeUnsignedVarInt(out, windowId);
        writeUnsignedVarInt(out, Math.max(0, size));
        for (int i = 0; i < size; i++) {
            writeUnsignedVarInt(out, 0); // air network id
        }
        return out;
    }

    /**
     * ITEM_STACK_RESPONSE — acknowledge request id with empty OK container.
     * Layout: responses count, each: result (byte), requestId (varint), containers…
     */
    public static ByteBuf itemStackResponseOk(int requestId) {
        ByteBuf out = Unpooled.buffer(24);
        writeUnsignedVarInt(out, BedrockPacketIds.ITEM_STACK_RESPONSE.id);
        writeUnsignedVarInt(out, 1); // responses
        out.writeByte(0); // OK
        writeUnsignedVarInt(out, requestId);
        writeUnsignedVarInt(out, 0); // container infos
        return out;
    }

    public static ItemStackRequestDecode tryDecodeItemStackRequest(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            int requestId = readSignedVarInt(body);
            int actionCount = readUnsignedVarInt(body);
            return new ItemStackRequestDecode(requestId, actionCount);
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    public record ItemStackRequestDecode(int requestId, int actionCount) {
    }

    public static void writeBlockPosition(ByteBuf out, int x, int y, int z) {
        writeSignedVarInt(out, x);
        writeUnsignedVarInt(out, y);
        writeSignedVarInt(out, z);
    }

    public static int[] readBlockPosition(ByteBuf in) {
        int x = readSignedVarInt(in);
        int y = readUnsignedVarInt(in);
        int z = readSignedVarInt(in);
        return new int[]{x, y, z};
    }

    public static void writeSignedVarInt(ByteBuf out, int value) {
        writeUnsignedVarInt(out, (value << 1) ^ (value >> 31));
    }

    public static int readSignedVarInt(ByteBuf in) {
        int raw = readUnsignedVarInt(in);
        return (raw >>> 1) ^ -(raw & 1);
    }

    public static PlayerActionDecode tryDecodePlayerAction(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            long entityId = readUnsignedVarInt(body);
            int action = readUnsignedVarInt(body);
            int[] pos = readBlockPosition(body);
            int resultFace = readUnsignedVarInt(body);
            return new PlayerActionDecode(entityId, action, pos[0], pos[1], pos[2], resultFace);
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    public record PlayerActionDecode(long entityRuntimeId, int action, int x, int y, int z, int face) {
        /** Start break / continue / abort / stop / etc. */
        public boolean isBreakRelated() {
            return action == 0 || action == 1 || action == 2 || action == 18;
        }

        public boolean isPlaceRelated() {
            return action == 25 || action == 26; // creative player / predict destroy variants vary
        }
    }

    public static InventoryTxDecode tryDecodeInventoryTransaction(ByteBuf body) {
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
                return new InventoryTxDecode(txType, requestId,
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

    public record InventoryTxDecode(int transactionType, int requestId, int x, int y, int z, boolean hasPos) {
        public boolean likelyUseItemOn() {
            return transactionType == 2 || transactionType == 3; // item use / item use on entity (version-dependent)
        }
    }

    public static AuthInputDecode tryDecodeAuthInput(ByteBuf body) {
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
            return new AuthInputDecode(x, y, z, pitch, yaw, headYaw, tick);
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    public record AuthInputDecode(float x, float y, float z, float pitch, float yaw, float headYaw, long tick) {
    }

    public static InteractDecode tryDecodeInteract(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            byte action = body.readByte();
            long targetRuntimeId = readUnsignedVarInt(body);
            return new InteractDecode(action, targetRuntimeId);
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    public record InteractDecode(byte action, long targetRuntimeId) {
    }

    public static Decoded decode(ByteBuf packet) {
        int id = readUnsignedVarInt(packet);
        return new Decoded(id, packet);
    }

    public record Decoded(int id, ByteBuf body) {
    }

    public static MoveDecode tryDecodeMove(ByteBuf body) {
        try {
            int runtimeId = readUnsignedVarInt(body);
            float x = body.readFloatLE();
            float y = body.readFloatLE();
            float z = body.readFloatLE();
            float pitch = body.readFloatLE();
            float yaw = body.readFloatLE();
            return new MoveDecode(runtimeId, x, y, z, pitch, yaw);
        } catch (Exception e) {
            return null;
        }
    }

    public record MoveDecode(int runtimeId, float x, float y, float z, float pitch, float yaw) {
    }

    public static TextDecode tryDecodeText(ByteBuf body) {
        try {
            int type = body.readUnsignedByte();
            boolean needsTranslation = body.readBoolean();
            String source = "";
            if (type == 1 || type == 3) {
                source = readString(body);
            }
            String message = readString(body);
            return new TextDecode(type, needsTranslation, source, message);
        } catch (Exception e) {
            return null;
        }
    }

    public record TextDecode(int type, boolean needsTranslation, String source, String message) {
    }
}

package com.yapcore.crossplay.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.List;
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

    /** Minimal StartGame matching bedrock 1.21.50 {@code packet_start_game} (79 fields). */
    public static ByteBuf startGame(long entityUniqueId, long runtimeId, String levelName,
                                    int blockX, int blockY, int blockZ, UUID worldId) {
        ByteBuf out = Unpooled.buffer(512);
        writeUnsignedVarInt(out, ID_START_GAME);
        // entity_id zigzag64, runtime_entity_id varint64
        writeZigZag64(out, entityUniqueId);
        writeUnsignedVarLong(out, runtimeId);
        writeSignedVarInt(out, 0); // player_gamemode survival
        out.writeFloatLE(blockX + 0.5f);
        out.writeFloatLE(blockY + 1.62f);
        out.writeFloatLE(blockZ + 0.5f);
        out.writeFloatLE(0f); // pitch
        out.writeFloatLE(0f); // yaw
        out.writeLongLE(0L); // seed
        out.writeShortLE(0); // biome_type
        writeString(out, "plains");
        writeSignedVarInt(out, 0); // dimension overworld
        writeSignedVarInt(out, 1); // generator
        writeSignedVarInt(out, 0); // world_gamemode
        out.writeBoolean(false); // hardcore
        writeSignedVarInt(out, 1); // difficulty normal
        // spawn_position BlockCoordinates
        writeSignedVarInt(out, blockX);
        writeUnsignedVarInt(out, blockY);
        writeSignedVarInt(out, blockZ);
        out.writeBoolean(true); // achievements_disabled
        writeSignedVarInt(out, 0); // editor_world_type not_editor
        out.writeBoolean(false); // created_in_editor
        out.writeBoolean(false); // exported_from_editor
        writeSignedVarInt(out, 0); // day_cycle_stop_time
        writeSignedVarInt(out, 0); // edu_offer
        out.writeBoolean(false); // edu_features_enabled
        writeString(out, ""); // edu_product_uuid
        out.writeFloatLE(0f); // rain_level
        out.writeFloatLE(0f); // lightning_level
        out.writeBoolean(false); // has_confirmed_platform_locked_content
        out.writeBoolean(true); // is_multiplayer
        out.writeBoolean(true); // broadcast_to_lan
        writeUnsignedVarInt(out, 4); // xbox_live_broadcast_mode (friends)
        writeUnsignedVarInt(out, 4); // platform_broadcast_mode
        out.writeBoolean(true); // enable_commands
        out.writeBoolean(false); // is_texturepacks_required
        writeUnsignedVarInt(out, 0); // gamerules
        out.writeIntLE(0); // experiments count (li32)
        out.writeBoolean(false); // experiments_previously_used
        out.writeBoolean(false); // bonus_chest
        out.writeBoolean(false); // map_enabled
        out.writeByte(1); // permission_level member
        out.writeIntLE(4); // server_chunk_tick_range
        out.writeBoolean(false); // has_locked_behavior_pack
        out.writeBoolean(false); // has_locked_resource_pack
        out.writeBoolean(false); // is_from_locked_world_template
        out.writeBoolean(false); // msa_gamertags_only
        out.writeBoolean(false); // is_from_world_template
        out.writeBoolean(false); // is_world_template_option_locked
        out.writeBoolean(false); // only_spawn_v1_villagers
        out.writeBoolean(false); // persona_disabled
        out.writeBoolean(false); // custom_skins_disabled
        out.writeBoolean(false); // emote_chat_muted
        writeString(out, "1.21.50"); // game_version
        out.writeIntLE(0); // limited_world_width
        out.writeIntLE(0); // limited_world_length
        out.writeBoolean(false); // is_new_nether
        writeString(out, ""); // edu_resource_uri.button_name
        writeString(out, ""); // edu_resource_uri.link_uri
        out.writeBoolean(false); // experimental_gameplay_override
        out.writeByte(0); // chat_restriction_level none
        out.writeBoolean(false); // disable_player_interactions
        writeString(out, "YaPcore"); // server_identifier
        writeString(out, "YaPcore"); // world_identifier
        writeString(out, ""); // scenario_identifier
        writeString(out, levelName == null ? "YaPcore" : levelName); // level_id
        writeString(out, levelName == null ? "YaPcore" : levelName); // world_name
        writeString(out, ""); // premium_world_template_id
        out.writeBoolean(false); // is_trial
        writeSignedVarInt(out, 0); // movement_authority client
        writeSignedVarInt(out, 0); // rewind_history_size
        out.writeBoolean(false); // server_authoritative_block_breaking
        out.writeLongLE(0L); // current_tick
        writeSignedVarInt(out, 0); // enchantment_seed
        writeUnsignedVarInt(out, 0); // block_properties
        BedrockItemStates.writeTo(out); // itemstates (vanilla table; needs MTU-split + deflate)
        writeString(out, ""); // multiplayer_correlation_id
        out.writeBoolean(false); // server_authoritative_inventory
        writeString(out, "YaPcore"); // engine
        writeEmptyNetworkNbt(out); // property_data
        out.writeLongLE(0L); // block_pallette_checksum
        // world_template_id uuid
        out.writeLongLE(worldId.getMostSignificantBits());
        out.writeLongLE(worldId.getLeastSignificantBits());
        out.writeBoolean(false); // client_side_generation
        out.writeBoolean(true); // block_network_ids_are_hashes (state ids = hashed runtime ids)
        out.writeBoolean(false); // server_controlled_sound
        return out;
    }

    /** Empty Bedrock network NBT compound (little-endian varint name lengths). */
    public static void writeEmptyNetworkNbt(ByteBuf out) {
        out.writeByte(0x0a); // TAG_Compound
        writeUnsignedVarInt(out, 0); // empty name (network / littleVarint)
        out.writeByte(0x00); // TAG_End
    }

    public static void writeZigZag64(ByteBuf out, long value) {
        writeUnsignedVarLong(out, (value << 1) ^ (value >> 63));
    }

    public static void writeUnsignedVarLong(ByteBuf out, long value) {
        while ((value & ~0x7FL) != 0L) {
            out.writeByte((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.writeByte((int) value);
    }

    public static long readUnsignedVarLong(ByteBuf in) {
        long value = 0;
        int size = 0;
        int b;
        while (((b = in.readUnsignedByte()) & 0x80) == 0x80) {
            value |= (long) (b & 0x7F) << (size++ * 7);
            if (size > 10) {
                throw new IllegalArgumentException("VarLong too big");
            }
        }
        return value | ((long) (b & 0x7F) << (size * 7));
    }

    public static ByteBuf resourcePacksInfoEmpty() {
        // 1.21.50 packet_resource_packs_info
        ByteBuf out = Unpooled.buffer(48);
        writeUnsignedVarInt(out, ID_RESOURCE_PACKS_INFO);
        out.writeBoolean(false); // must_accept
        out.writeBoolean(false); // has_addons
        out.writeBoolean(false); // has_scripts
        // world_template: uuid + version string
        out.writeLongLE(0L);
        out.writeLongLE(0L);
        writeString(out, "0.0.0");
        out.writeShortLE(0); // texture_packs count (li16)
        return out;
    }

    public static ByteBuf resourcePackStackEmpty() {
        // 1.21.50 packet_resource_pack_stack
        ByteBuf out = Unpooled.buffer(32);
        writeUnsignedVarInt(out, ID_RESOURCE_PACK_STACK);
        out.writeBoolean(false); // must_accept
        writeUnsignedVarInt(out, 0); // behavior_packs
        writeUnsignedVarInt(out, 0); // resource_packs
        writeString(out, "1.21.50");
        out.writeIntLE(0); // experiments count (li32)
        out.writeBoolean(false); // experiments_previously_used
        out.writeBoolean(false); // has_editor_packs
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

    /**
     * P4.6 — container_open. Window types: 0=chest, 2=furnace, 3=enchant, 15=villager / trading.
     * Layout (1.21.50): window_id u8, window_type i8/-zigzag, BlockCoordinates, runtime_entity_id zigzag64.
     */
    public static ByteBuf containerOpen(int windowId, int windowType, int x, int y, int z, long entityRuntimeId) {
        ByteBuf out = Unpooled.buffer(32);
        writeUnsignedVarInt(out, ID_CONTAINER_OPEN);
        out.writeByte(windowId & 0xFF);
        writeSignedVarInt(out, windowType);
        writeBlockPosition(out, x, y, z);
        writeSignedVarLong(out, entityRuntimeId);
        return out;
    }

    /**
     * PLAYER_ENCHANT_OPTIONS — list of enchant choices for the open table.
     */
    public static ByteBuf playerEnchantOptions(java.util.List<BedrockPaperRecipes.EnchantOption> options) {
        ByteBuf out = Unpooled.buffer(64);
        writeUnsignedVarInt(out, BedrockPacketIds.PLAYER_ENCHANT_OPTIONS.id);
        int n = options == null ? 0 : Math.min(options.size(), 3);
        writeUnsignedVarInt(out, n);
        for (int i = 0; i < n; i++) {
            BedrockPaperRecipes.EnchantOption o = options.get(i);
            writeSignedVarInt(out, o.cost()); // min cost / level
            out.writeIntLE(0); // primary slot
            // enchants0 — primary list
            writeUnsignedVarInt(out, 1);
            out.writeByte(o.enchantType() & 0xFF);
            out.writeByte(Math.max(1, o.enchantLevel()) & 0xFF);
            writeUnsignedVarInt(out, 0); // enchants1
            writeUnsignedVarInt(out, 0); // enchants2
            writeString(out, o.primaryName() == null ? "enchant" : o.primaryName());
            writeUnsignedVarInt(out, o.netId());
        }
        return out;
    }

    public static ByteBuf containerClose(int windowId, boolean serverInitiated) {
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, ID_CONTAINER_CLOSE);
        out.writeByte(windowId & 0xFF);
        out.writeBoolean(serverInitiated);
        return out;
    }

    /**
     * CONTAINER_SET_DATA — property/value pairs for furnace progress / enchant costs.
     * Enchant table typically uses property 0..2 for option costs.
     */
    public static ByteBuf containerSetData(int windowId, int property, int value) {
        ByteBuf out = Unpooled.buffer(16);
        writeUnsignedVarInt(out, BedrockPacketIds.CONTAINER_SET_DATA.id);
        out.writeByte(windowId & 0xFF);
        writeSignedVarInt(out, property);
        writeSignedVarInt(out, value);
        return out;
    }

    /**
     * UPDATE_TRADE — shallow villager offers for 1.21.50-style clients.
     * Each offer: buyA + optional buyB + sell as item legacy stubs (network id + count).
     */
    public static ByteBuf updateTrade(int windowId, int windowType, int size, int tradeTier,
                                      boolean recipeAdded, boolean isEconomy, long traderEntityId,
                                      long playerEntityId, String displayName,
                                      java.util.List<int[]> offers) {
        ByteBuf out = Unpooled.buffer(256);
        writeUnsignedVarInt(out, BedrockPacketIds.UPDATE_TRADE.id);
        out.writeByte(windowId & 0xFF);
        writeSignedVarInt(out, windowType);
        writeSignedVarInt(out, size);
        writeSignedVarInt(out, tradeTier);
        out.writeBoolean(recipeAdded);
        out.writeBoolean(isEconomy);
        writeSignedVarLong(out, traderEntityId);
        writeSignedVarLong(out, playerEntityId);
        writeString(out, displayName == null ? "Villager" : displayName);
        // Remaining demand / recipe nbt — send empty compound list via offer count
        int n = offers == null ? 0 : Math.min(offers.size(), 64);
        writeUnsignedVarInt(out, n);
        for (int i = 0; i < n; i++) {
            int[] o = offers.get(i);
            // buyA
            writeItemLegacyTrade(out, o.length > 0 ? o[0] : 0, o.length > 1 ? o[1] : 0);
            // sell
            writeItemLegacyTrade(out, o.length > 4 ? o[4] : 0, o.length > 5 ? o[5] : 0);
            out.writeBoolean(o.length > 2 && o[2] > 0); // has buyB
            if (o.length > 2 && o[2] > 0) {
                writeItemLegacyTrade(out, o[2], o.length > 3 ? o[3] : 1);
            }
            out.writeBoolean(true); // enabled
            writeSignedVarInt(out, -1); // uses
            writeSignedVarInt(out, Integer.MAX_VALUE); // max uses
            writeSignedVarInt(out, 0); // trader exp
            writeSignedVarInt(out, 0); // reward exp
            writeSignedVarInt(out, 0); // price multiplier
            writeSignedVarInt(out, 0); // demand
        }
        return out;
    }

    private static void writeItemLegacyTrade(ByteBuf out, int networkId, int count) {
        writeSignedVarInt(out, networkId);
        if (networkId != 0) {
            out.writeShortLE(Math.max(1, count) & 0xFFFF);
            writeUnsignedVarInt(out, 0); // metadata
            writeSignedVarInt(out, 0); // block_runtime
            writeUnsignedVarInt(out, 0); // user data / nbt empty
        }
    }

    public static ContainerCloseDecode tryDecodeContainerClose(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            int windowId = body.readUnsignedByte();
            boolean server = body.isReadable() && body.readBoolean();
            return new ContainerCloseDecode(windowId, server);
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    public record ContainerCloseDecode(int windowId, boolean serverInitiated) {
    }

    /**
     * Matches bedrock-protocol {@code packet_network_settings}: lu16 threshold, lu16 algorithm,
     * bool throttle, u8 throttle threshold, lf32 throttle scalar.
     */
    public static ByteBuf networkSettings(int compressionThreshold, int compressionAlgorithm,
                                          boolean clientThrottle, int clientThrottleThreshold,
                                          float clientThrottleScalar) {
        ByteBuf out = Unpooled.buffer(16);
        writeUnsignedVarInt(out, ID_NETWORK_SETTINGS);
        out.writeShortLE(compressionThreshold);
        out.writeShortLE(compressionAlgorithm);
        out.writeBoolean(clientThrottle);
        out.writeByte(clientThrottleThreshold & 0xff);
        out.writeFloatLE(clientThrottleScalar);
        return out;
    }

    /**
     * Disable client compression: lu16 max threshold so payloads never exceed it
     * ({@code buf.length > threshold} is false). Threshold 0 would compress everything
     * in bedrock-protocol's framer.
     */
    public static ByteBuf networkSettingsUncompressed() {
        return networkSettings(65535, 0, false, 0, 0f);
    }

    public static ByteBuf chunkRadiusUpdated(int radius) {
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, BedrockPacketIds.CHUNK_RADIUS_UPDATED.id);
        writeUnsignedVarInt(out, radius);
        return out;
    }

    public static ByteBuf networkChunkPublisherUpdate(int blockX, int blockY, int blockZ, int radiusBlocks) {
        ByteBuf out = Unpooled.buffer(32);
        writeUnsignedVarInt(out, 0x79); // NETWORK_CHUNK_PUBLISHER_UPDATE
        // BlockCoordinates: zigzag32 x, varint y, zigzag32 z
        writeZigZag32(out, blockX);
        writeUnsignedVarInt(out, blockY);
        writeZigZag32(out, blockZ);
        writeUnsignedVarInt(out, radiusBlocks);
        out.writeIntLE(0); // saved_chunks count (lu32)
        return out;
    }

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

    /** Full ADD_PLAYER matching 1.21.50 {@code packet_add_player}. */
    public static ByteBuf addPlayer(UUID uuid, String username, long runtimeId,
                                    float x, float y, float z, float yaw, float pitch) {
        ByteBuf out = Unpooled.buffer(192 + (username == null ? 0 : username.length()));
        writeUnsignedVarInt(out, ID_ADD_PLAYER);
        out.writeLongLE(uuid.getMostSignificantBits());
        out.writeLongLE(uuid.getLeastSignificantBits());
        writeString(out, username == null ? "" : username);
        writeUnsignedVarLong(out, runtimeId);
        writeString(out, ""); // platform_chat_id
        out.writeFloatLE(x);
        out.writeFloatLE(y);
        out.writeFloatLE(z);
        out.writeFloatLE(0f); // velocity
        out.writeFloatLE(0f);
        out.writeFloatLE(0f);
        out.writeFloatLE(pitch);
        out.writeFloatLE(yaw);
        out.writeFloatLE(yaw); // head_yaw
        writeSignedVarInt(out, 0); // held_item air
        writeSignedVarInt(out, 0); // gamemode survival
        writePlayerMetadata(out, username);
        writeUnsignedVarInt(out, 0); // properties ints
        writeUnsignedVarInt(out, 0); // properties floats
        out.writeLongLE(runtimeId); // unique_id li64
        out.writeByte(1); // permission_level member
        out.writeByte(0); // command_permission normal
        // abilities: one base layer
        out.writeByte(1); // layer count
        out.writeShortLE(1); // type = base
        out.writeIntLE(0x000D3FFF); // allowed — build/mine/doors/containers/attack/fly bits
        out.writeIntLE(0x000D3FFF); // enabled
        out.writeFloatLE(0.05f); // fly_speed
        out.writeFloatLE(0.1f); // walk_speed
        writeUnsignedVarInt(out, 0); // links
        writeString(out, ""); // device_id
        out.writeIntLE(1); // device_os Android (harmless for PC viewers)
        return out;
    }

    static void writePlayerMetadata(ByteBuf out, String nametag) {
        writeDenseMetadata(out, nametag, 20f, 0.6f, 1.8f, true);
    }

    static void writeActorMetadata(ByteBuf out, String actorTypeOrNametag) {
        float[] aabb = aabbForActor(actorTypeOrNametag);
        writeDenseMetadata(out, actorTypeOrNametag, 20f, aabb[0], aabb[1], false);
    }

    /** G.25 — SET_ACTOR_DATA live update (health / nametag / AABB). */
    public static ByteBuf setActorData(long runtimeId, String nametag, float health,
                                       float width, float height) {
        ByteBuf out = Unpooled.buffer(96);
        writeUnsignedVarInt(out, BedrockPacketIds.SET_ACTOR_DATA.id);
        writeUnsignedVarLong(out, runtimeId);
        writeDenseMetadata(out, nametag, health, width, height, false);
        writeUnsignedVarInt(out, 0); // property sync ints
        writeUnsignedVarInt(out, 0); // property sync floats
        writeUnsignedVarLong(out, 0L); // tick
        return out;
    }

    public static ByteBuf setActorData(long runtimeId, String actorType, String nametag, float health) {
        float[] aabb = aabbForActor(actorType);
        return setActorData(runtimeId, nametag == null || nametag.isBlank() ? actorType : nametag,
                health, aabb[0], aabb[1]);
    }

    /**
     * Dense MetadataDictionary: flags, health, nametag, optional air, scale, AABB.
     * Key/type ids match bedrock 1.21.50 protocol.json.
     */
    static void writeDenseMetadata(ByteBuf out, String nametag, float health,
                                   float width, float height, boolean includeAir) {
        writeUnsignedVarInt(out, includeAir ? 7 : 6);
        writeUnsignedVarInt(out, 0); // flags
        writeUnsignedVarInt(out, 7); // long
        writeZigZag64(out, (1L << 14) | (1L << 15) | (1L << 19) | (1L << 22));
        writeUnsignedVarInt(out, 1); // health
        writeUnsignedVarInt(out, 3); // float
        out.writeFloatLE(Math.max(0f, health));
        writeUnsignedVarInt(out, 4); // nametag
        writeUnsignedVarInt(out, 4); // string
        writeString(out, nametag == null ? "" : nametag);
        if (includeAir) {
            writeUnsignedVarInt(out, 7); // air
            writeUnsignedVarInt(out, 1); // short
            out.writeShortLE(300);
        }
        writeUnsignedVarInt(out, 38); // scale
        writeUnsignedVarInt(out, 3);
        out.writeFloatLE(1f);
        writeUnsignedVarInt(out, 53); // boundingbox_width
        writeUnsignedVarInt(out, 3);
        out.writeFloatLE(width);
        writeUnsignedVarInt(out, 54); // boundingbox_height
        writeUnsignedVarInt(out, 3);
        out.writeFloatLE(height);
    }

    /** Approx Bedrock AABB by entity identifier (player default 0.6×1.8). */
    static float[] aabbForActor(String actorType) {
        if (actorType == null) {
            return new float[]{0.6f, 1.8f};
        }
        String t = actorType.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("player")) {
            return new float[]{0.6f, 1.8f};
        }
        if (t.contains("enderman")) {
            return new float[]{0.6f, 2.9f};
        }
        if (t.contains("iron_golem")) {
            return new float[]{1.4f, 2.7f};
        }
        if (t.contains("villager") || t.contains("zombie") || t.contains("skeleton")
                || t.contains("creeper") || t.contains("witch") || t.contains("pillager")) {
            return new float[]{0.6f, 1.95f};
        }
        if (t.contains("spider") || t.contains("cave_spider")) {
            return new float[]{1.4f, 0.9f};
        }
        if (t.contains("chicken") || t.contains("bat") || t.contains("parrot") || t.contains("bee")) {
            return new float[]{0.4f, 0.7f};
        }
        if (t.contains("cow") || t.contains("pig") || t.contains("sheep") || t.contains("wolf")
                || t.contains("fox") || t.contains("goat")) {
            return new float[]{0.9f, 0.9f};
        }
        if (t.contains("horse") || t.contains("donkey") || t.contains("mule") || t.contains("camel")) {
            return new float[]{1.4f, 1.6f};
        }
        if (t.contains("slime") || t.contains("magma")) {
            return new float[]{0.51f, 0.51f};
        }
        if (t.contains("ghast")) {
            return new float[]{4f, 4f};
        }
        if (t.contains("wither")) {
            return new float[]{0.9f, 3.5f};
        }
        if (t.contains("dragon")) {
            return new float[]{16f, 8f};
        }
        if (t.contains("armor_stand")) {
            return new float[]{0.5f, 1.975f};
        }
        if (t.contains("item") || t.contains("xp_orb") || t.contains("experience")) {
            return new float[]{0.25f, 0.25f};
        }
        return new float[]{0.6f, 1.8f};
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
        writeActorMetadata(out, actorType);
        writeUnsignedVarInt(out, 0); // entity links
        return out;
    }

    public static ByteBuf removeActor(long uniqueEntityId) {
        ByteBuf out = Unpooled.buffer(16);
        writeUnsignedVarInt(out, ID_REMOVE_ENTITY);
        out.writeLongLE(uniqueEntityId);
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

    /**
     * Default player attributes (health / movement / hunger) for 1.21.50 PlayerAttributes.
     */
    public static ByteBuf updateAttributesDefault(long runtimeId) {
        ByteBuf out = Unpooled.buffer(128);
        writeUnsignedVarInt(out, BedrockPacketIds.UPDATE_ATTRIBUTES.id);
        writeUnsignedVarLong(out, runtimeId);
        String[] names = {
                "minecraft:health",
                "minecraft:player.hunger",
                "minecraft:movement",
                "minecraft:player.level",
                "minecraft:player.experience"
        };
        float[][] vals = {
                {0f, 20f, 20f, 0f, 20f, 20f},
                {0f, 20f, 20f, 0f, 20f, 20f},
                {0f, 3.4028e38f, 0.1f, 0f, 3.4028e38f, 0.1f},
                {0f, 24791f, 0f, 0f, 24791f, 0f},
                {0f, 1f, 0f, 0f, 1f, 0f}
        };
        writeUnsignedVarInt(out, names.length);
        for (int i = 0; i < names.length; i++) {
            out.writeFloatLE(vals[i][0]); // min
            out.writeFloatLE(vals[i][1]); // max
            out.writeFloatLE(vals[i][2]); // current
            out.writeFloatLE(vals[i][3]); // default_min
            out.writeFloatLE(vals[i][4]); // default_max
            out.writeFloatLE(vals[i][5]); // default
            writeString(out, names[i]);
            writeUnsignedVarInt(out, 0); // modifiers
        }
        writeUnsignedVarLong(out, 0L); // tick
        return out;
    }

    /** Empty creative inventory (varint count 0). */
    public static ByteBuf creativeContentEmpty() {
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, BedrockPacketIds.CREATIVE_CONTENT.id);
        writeUnsignedVarInt(out, 0);
        return out;
    }

    /**
     * Full creative catalog from vanilla itemstates (ItemLegacy entries).
     * Skips air; shield gets blocking_tick=0 extra.
     */
    public static ByteBuf creativeContentFull() {
        List<BedrockItemStates.ItemState> states = BedrockItemStates.all();
        ByteBuf out = Unpooled.buffer(Math.max(64, states.size() * 24));
        writeUnsignedVarInt(out, BedrockPacketIds.CREATIVE_CONTENT.id);
        int count = 0;
        for (BedrockItemStates.ItemState s : states) {
            if (s.runtimeId() == 0 || "minecraft:air".equals(s.name())) {
                continue;
            }
            count++;
        }
        writeUnsignedVarInt(out, count);
        int entryId = 1;
        for (BedrockItemStates.ItemState s : states) {
            if (s.runtimeId() == 0 || "minecraft:air".equals(s.name())) {
                continue;
            }
            writeUnsignedVarInt(out, entryId++);
            writeItemLegacy(out, s.runtimeId() & 0xFFFF);
        }
        return out;
    }

    /** ItemLegacy for creative / inventory (network_id != 0). */
    static void writeItemLegacy(ByteBuf out, int networkId) {
        writeItemLegacy(out, networkId, 1);
    }

    static void writeItemLegacy(ByteBuf out, int networkId, int count) {
        writeSignedVarInt(out, networkId);
        out.writeShortLE(Math.max(1, Math.min(64, count)));
        writeUnsignedVarInt(out, 0); // metadata
        writeSignedVarInt(out, 0); // block_runtime_id
        ByteBuf extra = Unpooled.buffer(16);
        extra.writeShortLE(0); // has_nbt false
        extra.writeIntLE(0); // can_place_on
        extra.writeIntLE(0); // can_destroy
        if (networkId == SHIELD_NETWORK_ID) {
            extra.writeLongLE(0L); // blocking_tick
        }
        writeUnsignedVarInt(out, extra.readableBytes());
        out.writeBytes(extra);
        extra.release();
    }

    /**
     * Minimal available_commands with zero enums/commands — enough for clients
     * that expect the packet before chat.
     */
    public static ByteBuf availableCommandsEmpty() {
        ByteBuf out = Unpooled.buffer(32);
        writeUnsignedVarInt(out, BedrockPacketIds.AVAILABLE_COMMANDS.id);
        writeUnsignedVarInt(out, 0); // values_len → enum type byte size becomes 0-path
        // chained_subcommand_values
        writeUnsignedVarInt(out, 0);
        // suffixes
        writeUnsignedVarInt(out, 0);
        // enums
        writeUnsignedVarInt(out, 0);
        // chained_subcommands
        writeUnsignedVarInt(out, 0);
        // commands
        writeUnsignedVarInt(out, 0);
        // dynamic_enums
        writeUnsignedVarInt(out, 0);
        // constraints
        writeUnsignedVarInt(out, 0);
        return out;
    }

    /** Rich vanilla-adjacent available_commands catalog (autocomplete UX). */
    public static ByteBuf availableCommandsRich() {
        return BedrockAvailableCommands.encodeDefault();
    }

    /** Player list add with one entry and minimal Skin (1.21.50). */
    public static ByteBuf playerListAddSelf(UUID uuid, long entityUniqueId, String username) {
        ByteBuf out = Unpooled.buffer(384 + (username == null ? 0 : username.length()));
        writeUnsignedVarInt(out, BedrockPacketIds.PLAYER_LIST.id);
        out.writeByte(0); // type add
        writeUnsignedVarInt(out, 1); // records_count
        out.writeLongLE(uuid.getMostSignificantBits());
        out.writeLongLE(uuid.getLeastSignificantBits());
        writeZigZag64(out, entityUniqueId);
        writeString(out, username == null ? "Player" : username);
        writeString(out, ""); // xbox_user_id
        writeString(out, ""); // platform_chat_id
        out.writeIntLE(0); // build_platform (li32)
        writeMinimalSkin(out);
        out.writeBoolean(false); // is_teacher
        out.writeBoolean(true); // is_host
        out.writeBoolean(false); // is_subclient
        out.writeBoolean(true); // verified[0]
        return out;
    }

    /** Minimal Skin matching 1.21.50 {@code Skin} + empty {@code SkinImage}s. */
    public static void writeMinimalSkin(ByteBuf out) {
        writeString(out, "Standard_Custom"); // skin_id
        writeString(out, ""); // play_fab_id
        writeString(out, "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}"); // skin_resource_pack
        writeEmptySkinImage(out); // skin_data
        out.writeIntLE(0); // animations count (li32)
        writeEmptySkinImage(out); // cape_data
        writeString(out, ""); // geometry_data
        writeString(out, "1.14.0"); // geometry_data_version
        writeString(out, ""); // animation_data
        writeString(out, ""); // cape_id
        writeString(out, "Standard_Custom"); // full_skin_id
        writeString(out, "wide"); // arm_size
        writeString(out, "#0"); // skin_color
        out.writeIntLE(0); // personal_pieces
        out.writeIntLE(0); // piece_tint_colors
        out.writeBoolean(false); // premium
        out.writeBoolean(false); // persona
        out.writeBoolean(false); // cape_on_classic
        out.writeBoolean(true); // primary_user
        out.writeBoolean(false); // overriding_player_appearance
    }

    private static void writeEmptySkinImage(ByteBuf out) {
        out.writeIntLE(0); // width
        out.writeIntLE(0); // height
        writeUnsignedVarInt(out, 0); // ByteArray data
    }

    /**
     * Empty inventory content for 1.21.50 {@code packet_inventory_content}:
     * window_id, ItemStacks(count), FullContainerName, storage Item(air).
     */
    public static ByteBuf inventoryContentEmpty(int windowId, int size) {
        int[] air = new int[Math.max(0, size)];
        return inventoryContent(windowId, air);
    }

    /**
     * Inventory content with ItemLegacy slots (0 = air). Used for Paper inventory authority push.
     */
    public static ByteBuf inventoryContent(int windowId, int[] networkIds) {
        return inventoryContent(windowId, networkIds, null);
    }

    /**
     * @param counts optional parallel stack sizes (clamped 1–64); null → count 1
     */
    public static ByteBuf inventoryContent(int windowId, int[] networkIds, int[] counts) {
        ByteBuf out = Unpooled.buffer(32 + networkIds.length * 16);
        writeUnsignedVarInt(out, ID_INVENTORY_CONTENT);
        writeUnsignedVarInt(out, windowId);
        writeUnsignedVarInt(out, networkIds.length);
        for (int i = 0; i < networkIds.length; i++) {
            int networkId = networkIds[i];
            if (networkId == 0) {
                writeSignedVarInt(out, 0);
            } else {
                int c = 1;
                if (counts != null && i < counts.length && counts[i] > 0) {
                    c = counts[i];
                }
                writeItemLegacy(out, networkId, c);
            }
        }
        out.writeByte(29); // inventory container
        out.writeByte(0);
        writeSignedVarInt(out, 0); // storage_item air
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
            java.util.List<StackAction> actions = new java.util.ArrayList<>();
            int requestId;
            int saved = body.readerIndex();
            int first = readUnsignedVarInt(body);
            // Protocol: requests[] count. If count is 1 and next looks like request_id, use array form.
            // Legacy test/path: first value is zigzag request_id directly.
            if (first == 1 && body.isReadable()) {
                int beforeReq = body.readerIndex();
                try {
                    requestId = readSignedVarInt(body);
                    int actionCount = readUnsignedVarInt(body);
                    parseStackActions(body, actionCount, actions);
                    return new ItemStackRequestDecode(requestId, actions.size(), List.copyOf(actions));
                } catch (Exception e) {
                    body.readerIndex(beforeReq);
                }
            }
            // Legacy: rewind and treat first as request_id zigzag
            body.readerIndex(saved);
            requestId = readSignedVarInt(body);
            int actionCount = readUnsignedVarInt(body);
            parseStackActions(body, actionCount, actions);
            return new ItemStackRequestDecode(requestId, Math.max(actionCount, actions.size()), List.copyOf(actions));
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    private static void parseStackActions(ByteBuf body, int actionCount,
                                          java.util.List<StackAction> out) {
        int n = Math.min(Math.max(0, actionCount), 64);
        for (int i = 0; i < n; i++) {
            if (body.readableBytes() < 1) {
                return;
            }
            int typeId = body.readUnsignedByte();
            try {
                switch (typeId) {
                    case 0, 1 -> { // take / place
                        int count = body.readUnsignedByte();
                        int src = readSlotInfoMapped(body);
                        int dst = readSlotInfoMapped(body);
                        out.add(new StackAction(typeId == 0 ? StackActionType.TAKE : StackActionType.PLACE,
                                src, dst, count, 0));
                    }
                    case 2 -> { // swap
                        int src = readSlotInfoMapped(body);
                        int dst = readSlotInfoMapped(body);
                        out.add(new StackAction(StackActionType.SWAP, src, dst, 0, 0));
                    }
                    case 3 -> { // drop
                        int count = body.readUnsignedByte();
                        int src = readSlotInfoMapped(body);
                        body.readBoolean(); // randomly
                        out.add(new StackAction(StackActionType.DROP, src, -1, count, 0));
                    }
                    case 4 -> { // destroy
                        int count = body.readUnsignedByte();
                        int src = readSlotInfoMapped(body);
                        out.add(new StackAction(StackActionType.DESTROY, src, -1, count, 0));
                    }
                    case 5 -> { // consume
                        int count = body.readUnsignedByte();
                        int src = readSlotInfoMapped(body);
                        out.add(new StackAction(StackActionType.CONSUME, src, -1, count, 0));
                    }
                    case 6 -> { // create — result lands on cursor
                        int results = body.readUnsignedByte();
                        int networkId = results > 0 ? creativeEntryToNetworkId(results) : 0;
                        out.add(new StackAction(StackActionType.CREATE, -1, -1, 1, networkId));
                    }
                    case 10, 11 -> { // craft_recipe / craft_recipe_auto
                        int recipeNetId = readUnsignedVarInt(body);
                        int times = body.isReadable() ? Math.max(1, body.readUnsignedByte()) : 1;
                        out.add(new StackAction(
                                typeId == 10 ? StackActionType.CRAFT_RECIPE : StackActionType.CRAFT_RECIPE_AUTO,
                                -1, -1, times, recipeNetId));
                    }
                    case 13 -> { // craft_recipe_optional (enchant option / filter trade)
                        int recipeNetId = readUnsignedVarInt(body);
                        int times = body.isReadable() ? Math.max(1, body.readUnsignedByte()) : 1;
                        out.add(new StackAction(StackActionType.CRAFT_RECIPE_OPTIONAL,
                                -1, -1, times, recipeNetId));
                    }
                    case 12, 14 -> { // craft_creative (12 modern / 14 legacy)
                        int itemId = readUnsignedVarInt(body);
                        int times = body.isReadable() ? body.readUnsignedByte() : 1;
                        int networkId = creativeEntryToNetworkId(itemId);
                        out.add(new StackAction(StackActionType.CRAFT_CREATIVE, -1, -1,
                                Math.max(1, times), networkId));
                    }
                    default -> {
                        return;
                    }
                }
            } catch (Exception e) {
                return;
            }
        }
    }

    /** FullContainerName + slot u8 + stack_id zigzag32 → packed (containerId<<16)|slot. */
    private static int readSlotInfoMapped(ByteBuf body) {
        int containerId = body.readUnsignedByte();
        // option&lt;u32&gt; dynamic_container_id
        if (body.readBoolean()) {
            body.readIntLE();
        }
        int slot = body.readUnsignedByte();
        readSignedVarInt(body); // stack_id
        return (containerId << 16) | (slot & 0xffff);
    }

    private static int creativeEntryToNetworkId(int entryId) {
        // creative_content entry_ids are 1-based over non-air itemstates
        int idx = 0;
        for (BedrockItemStates.ItemState s : BedrockItemStates.all()) {
            if (s.runtimeId() == 0 || "minecraft:air".equals(s.name())) {
                continue;
            }
            idx++;
            if (idx == entryId) {
                return s.runtimeId() & 0xFFFF;
            }
        }
        return entryId; // fallback: treat as network id
    }

    public enum StackActionType {
        TAKE, PLACE, SWAP, DROP, DESTROY, CONSUME, CREATE,
        CRAFT_RECIPE, CRAFT_RECIPE_AUTO, CRAFT_RECIPE_OPTIONAL, CRAFT_CREATIVE
    }

    public record StackAction(StackActionType type, int sourceSlot, int destSlot,
                              int count, int creativeNetworkId) {
    }

    public record ItemStackRequestDecode(int requestId, int actionCount,
                                         java.util.List<StackAction> actions) {
        public ItemStackRequestDecode(int requestId, int actionCount) {
            this(requestId, actionCount, java.util.List.of());
        }
    }

    public static MobEquipmentDecode tryDecodeMobEquipment(ByteBuf body) {
        int mark = body.readerIndex();
        try {
            long runtimeId = readUnsignedVarLong(body);
            // ItemLegacy — may be air
            int networkId = readSignedVarInt(body);
            if (networkId != 0) {
                body.readUnsignedShortLE(); // count
                readUnsignedVarInt(body); // metadata
                readSignedVarInt(body); // block_runtime_id
                int extraLen = readUnsignedVarInt(body);
                if (extraLen > 0 && body.readableBytes() >= extraLen) {
                    body.skipBytes(extraLen);
                }
            }
            int inventorySlot = body.readUnsignedByte();
            int hotbarSlot = body.readUnsignedByte();
            int windowId = body.readUnsignedByte();
            return new MobEquipmentDecode(runtimeId, networkId, inventorySlot, hotbarSlot, windowId);
        } catch (Exception e) {
            body.readerIndex(mark);
            return null;
        }
    }

    public record MobEquipmentDecode(long runtimeId, int networkId, int inventorySlot,
                                      int hotbarSlot, int windowId) {
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

    public static void writeSignedVarLong(ByteBuf out, long value) {
        writeUnsignedVarLong(out, (value << 1) ^ (value >> 63));
    }

    /** Alias for zigzag32 (same encoding as signed VarInt). */
    public static void writeZigZag32(ByteBuf out, int value) {
        writeSignedVarInt(out, value);
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

    /** Decode {@code packet_command_request} — command string is first field. */
    public static CommandRequestDecode tryDecodeCommandRequest(ByteBuf body) {
        try {
            String command = readString(body);
            return new CommandRequestDecode(command == null ? "" : command);
        } catch (Exception e) {
            return null;
        }
    }

    public record CommandRequestDecode(String command) {
    }

    /**
     * Minimal {@code command_output} success/failure toast for the requesting player.
     * Schema: origin (player) + success bool + message count + messages.
     */
    public static ByteBuf commandOutputSimple(String message, boolean success) {
        ByteBuf out = Unpooled.buffer(64 + (message == null ? 0 : message.length()));
        writeUnsignedVarInt(out, BedrockPacketIds.COMMAND_OUTPUT.id);
        // CommandOrigin: player
        writeUnsignedVarInt(out, 0);
        out.writeLongLE(0L);
        out.writeLongLE(0L);
        writeString(out, ""); // request_id
        // player_entity_id switch void for type=player
        out.writeByte(3); // output_type = all
        writeUnsignedVarInt(out, success ? 1 : 0); // success_count
        writeUnsignedVarInt(out, 1); // output messages
        out.writeBoolean(success);
        writeString(out, message == null ? "" : message);
        writeUnsignedVarInt(out, 0); // params
        return out;
    }
}

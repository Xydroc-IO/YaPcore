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

public final class BedrockLoginCodec {
    private BedrockLoginCodec() {}
    public static ByteBuf playStatus(BedrockPacketCodec.PlayStatus status) {
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_PLAY_STATUS);
        out.writeInt(status.code);
        return out;
    }
    public static ByteBuf startGame(long entityUniqueId, long runtimeId, String levelName,
                                    int blockX, int blockY, int blockZ, UUID worldId) {
        ByteBuf out = Unpooled.buffer(512);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_START_GAME);
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
    public static ByteBuf resourcePacksInfoEmpty() {
        return resourcePacksInfoOffer(null, "0.0.0", 0L, "", false);
    }

    /** Bedrock resource pack offer mirrored from JE pack HTTP (G.34). */
    public static ByteBuf resourcePacksInfoOffer(UUID packId, String version, long sizeBytes,
                                                 String cdnUrl, boolean mustAccept) {
        ByteBuf out = Unpooled.buffer(128);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_RESOURCE_PACKS_INFO);
        out.writeBoolean(mustAccept);
        out.writeBoolean(false); // has_addons
        out.writeBoolean(false); // has_scripts
        out.writeBoolean(false); // force_disable_vibrant_visuals
        UUID wt = packId != null ? packId : new UUID(0L, 0L);
        out.writeLongLE(wt.getMostSignificantBits());
        out.writeLongLE(wt.getLeastSignificantBits());
        writeString(out, version == null ? "0.0.0" : version);
        if (packId == null || cdnUrl == null || cdnUrl.isBlank()) {
            out.writeShortLE(0);
            return out;
        }
        out.writeShortLE(1);
        out.writeLongLE(packId.getMostSignificantBits());
        out.writeLongLE(packId.getLeastSignificantBits());
        writeString(out, version == null ? "0.0.0" : version);
        out.writeLongLE(Math.max(0L, sizeBytes));
        writeString(out, "");
        writeString(out, "");
        writeString(out, "");
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        writeString(out, cdnUrl);
        return out;
    }

    public static ByteBuf resourcePackStackEmpty() {
        // 1.21.50 packet_resource_pack_stack
        ByteBuf out = Unpooled.buffer(32);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_RESOURCE_PACK_STACK);
        out.writeBoolean(false); // must_accept
        writeUnsignedVarInt(out, 0); // behavior_packs
        writeUnsignedVarInt(out, 0); // resource_packs
        writeString(out, "1.21.50");
        out.writeIntLE(0); // experiments count (li32)
        out.writeBoolean(false); // experiments_previously_used
        out.writeBoolean(false); // has_editor_packs
        return out;
    }

    public static ByteBuf resourcePackStackOffer(UUID packId, String version, boolean mustAccept) {
        if (packId == null) {
            return resourcePackStackEmpty();
        }
        ByteBuf out = Unpooled.buffer(64);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_RESOURCE_PACK_STACK);
        out.writeBoolean(mustAccept);
        writeUnsignedVarInt(out, 0); // behavior_packs
        writeUnsignedVarInt(out, 1); // resource_packs
        out.writeLongLE(packId.getMostSignificantBits());
        out.writeLongLE(packId.getLeastSignificantBits());
        writeString(out, version == null ? "0.0.0" : version);
        writeString(out, "");
        writeString(out, "1.21.50");
        out.writeIntLE(0);
        out.writeBoolean(false);
        out.writeBoolean(false);
        return out;
    }

    public static ByteBuf modalFormRequest(int formId, String json) {
        ByteBuf out = Unpooled.buffer(16 + json.length());
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_MODAL_FORM_REQUEST);
        writeUnsignedVarInt(out, formId);
        writeString(out, json);
        return out;
    }

    public static ByteBuf playerSkin(UUID uuid, String skinId, String skinDataBase64, String capeData, String geometry) {
        ByteBuf out = Unpooled.buffer(128);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_PLAYER_SKIN);
        out.writeLongLE(uuid.getMostSignificantBits());
        out.writeLongLE(uuid.getLeastSignificantBits());
        writeString(out, skinId);
        writeString(out, skinDataBase64 == null ? "" : skinDataBase64);
        writeString(out, capeData == null ? "" : capeData);
        writeString(out, geometry == null ? "geometry.humanoid.custom" : geometry);
        return out;
    }
    public static ByteBuf networkSettings(int compressionThreshold, int compressionAlgorithm,
                                          boolean clientThrottle, int clientThrottleThreshold,
                                          float clientThrottleScalar) {
        ByteBuf out = Unpooled.buffer(16);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_NETWORK_SETTINGS);
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
}

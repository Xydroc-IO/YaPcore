package com.yapcore.crossplay.bedrock.codec;

import com.yapcore.crossplay.bedrock.BedrockAvailableCommands;
import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.bedrock.BedrockPacketIds;
import com.yapcore.crossplay.skin.BedrockCanonicalSkin;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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

    /** ServerToClientHandshake — body is a single string (JWT). */
    public static ByteBuf serverToClientHandshake(String jwt) {
        ByteBuf out = Unpooled.buffer(64 + (jwt == null ? 0 : jwt.length()));
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_SERVER_TO_CLIENT_HANDSHAKE);
        writeString(out, jwt == null ? "" : jwt);
        return out;
    }

    /**
     * StartGame. Defaults to Cloudburst {@code StartGameSerializer_v2168} (proto ≥2168 / 2207 clients).
     * Prefers {@link #startGame(long, long, String, int, int, int, UUID, int)} with the session protocol.
     */
    public static ByteBuf startGame(long entityUniqueId, long runtimeId, String levelName,
                                    int blockX, int blockY, int blockZ, UUID worldId) {
        return BedrockLoginStartGameCodec.startGame(
                entityUniqueId, runtimeId, levelName, blockX, blockY, blockZ, worldId);
    }

    /**
     * Protocol-branched StartGame matching Cloudburst:
     * <ul>
     *   <li>≥2168: {@code StartGameSerializer_v2168} — unsigned edu, byte permission, editor fields,
     *       2-field movement, no itemstates, ServerJoinInfo optional + telemetry at tail</li>
     *   <li>&lt;2168 (776 layout): signed edu, varint permission, ServerId/WorldId/ScenarioId in
     *       level settings, 3-field movement (mode+rewind+breaking), no editor/join-info/tail telemetry</li>
     * </ul>
     * Itemstates omitted in both (≥776 → ItemRegistry / ItemComponent 0xa2).
     */
    public static ByteBuf startGame(long entityUniqueId, long runtimeId, String levelName,
                                    int blockX, int blockY, int blockZ, UUID worldId, int protocol) {
        return BedrockLoginStartGameCodec.startGame(
                entityUniqueId, runtimeId, levelName, blockX, blockY, blockZ, worldId, protocol);
    }

    /**
     * ItemRegistry / ItemComponent (Cloudburst id 162 = 0xa2) — replaces StartGame itemstates for ≥776.
     * Entry layout: ItemComponentSerializer_v776 — name, shortLE runtimeId, componentBased, version varint, NBT.
     */
    public static ByteBuf itemRegistry() {
        return BedrockLoginStartGameCodec.itemRegistry();
    }

    /**
     * ItemComponent (0xa2). Cloudburst {@code ItemComponentSerializer_v776} entry layout is correct,
     * but our dump is protocol/bedrock/1.21.50 — wrong runtime ids for 1.26.x / proto 2207 and hard-crashes.
     * Empty registry lets the client use its built-in palette (connect first; per-proto dumps later).
     */
    public static ByteBuf itemRegistry(int protocol) {
        return BedrockLoginStartGameCodec.itemRegistry(protocol);
    }

    public static ByteBuf resourcePacksInfoEmpty() {
        return BedrockLoginPackCodec.resourcePacksInfoEmpty();
    }

    /** Bedrock resource pack offer mirrored from JE pack HTTP (G.34). Defaults to modern (≥2168) layout. */
    public static ByteBuf resourcePacksInfoOffer(UUID packId, String version, long sizeBytes,
                                                 String cdnUrl, boolean mustAccept) {
        return BedrockLoginPackCodec.resourcePacksInfoOffer(packId, version, sizeBytes, cdnUrl, mustAccept);
    }

    /**
     * ResourcePacksInfo. Pack-list length encoding:
     * <ul>
     *   <li>≥2168 (Cloudburst {@code ResourcePacksInfoSerializer_v2168}): unsigned varint</li>
     *   <li>&lt;2168: unsigned short LE (legacy)</li>
     * </ul>
     * Writing shortLE to a 2207 client crashes immediately after LOGIN_SUCCESS+packs — confirmed die stage.
     */
    public static ByteBuf resourcePacksInfoOffer(UUID packId, String version, long sizeBytes,
                                                 String cdnUrl, boolean mustAccept, int protocol) {
        return BedrockLoginPackCodec.resourcePacksInfoOffer(
                packId, version, sizeBytes, cdnUrl, mustAccept, protocol);
    }

    public static ByteBuf resourcePackStackEmpty() {
        return BedrockLoginPackCodec.resourcePackStackEmpty();
    }

    /**
     * ResourcePackStack. Cloudburst {@code ResourcePackStackSerializer_v898}+ drops behavior_packs;
     * only resource packs remain before gameVersion.
     */
    public static ByteBuf resourcePackStackEmpty(int protocol) {
        return BedrockLoginPackCodec.resourcePackStackEmpty(protocol);
    }

    public static ByteBuf resourcePackStackOffer(UUID packId, String version, boolean mustAccept) {
        return BedrockLoginPackCodec.resourcePackStackOffer(packId, version, mustAccept);
    }

    public static ByteBuf resourcePackStackOffer(UUID packId, String version, boolean mustAccept, int protocol) {
        return BedrockLoginPackCodec.resourcePackStackOffer(packId, version, mustAccept, protocol);
    }

    public static ByteBuf modalFormRequest(int formId, String json) {
        ByteBuf out = Unpooled.buffer(16 + json.length());
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_MODAL_FORM_REQUEST);
        writeUnsignedVarInt(out, formId);
        writeString(out, json);
        return out;
    }

    /**
     * Legacy string-payload PlayerSkin (pre-full Skin structure). Prefer the byte[] overload.
     */
    public static ByteBuf playerSkin(UUID uuid, String skinId, String skinDataBase64, String capeData, String geometry) {
        return BedrockLoginSkinCodec.playerSkin(uuid, skinId, skinDataBase64, capeData, geometry);
    }

    /**
     * Full PlayerSkin with real SkinImage bytes (PNG decoded to RGBA, or raw RGBA).
     * Builds a temporary classic {@link BedrockCanonicalSkin} so {@code geometry_data} is never empty
     * when a PNG is present.
     */
    public static ByteBuf playerSkin(UUID uuid, String skinId, byte[] skinRgbaOrPng, byte[] cape,
                                     String geometry, boolean slim, int protocol) {
        return BedrockLoginSkinCodec.playerSkin(uuid, skinId, skinRgbaOrPng, cape, geometry, slim, protocol);
    }

    /**
     * PlayerSkin from Bedrock-canonical payload (Phase 1). Writes full Skin fields including
     * non-empty {@code geometry_data}, persona pieces, and animations when present.
     */
    public static ByteBuf playerSkin(BedrockCanonicalSkin skin, int protocol) {
        return BedrockLoginSkinCodec.playerSkin(skin, protocol);
    }

    /**
     * Parse a PlayerSkin packet body (UUID + Skin [+ optional new/old names + trusted]) into
     * {@link BedrockCanonicalSkin}. Prefer Cloudburst {@code helper.readSkin} when
     * {@code protocol >= 2168}; otherwise mirror the pre-2168 canonical Skin write path.
     */
    public static BedrockCanonicalSkin readPlayerSkinBody(ByteBuf body, int protocol) {
        return BedrockLoginSkinCodec.readPlayerSkinBody(body, protocol);
    }

    /** Mirror of the pre-2168 canonical Skin write path (string arm_size / piece types). */
    public static BedrockCanonicalSkin readCanonicalSkin(ByteBuf in, UUID uuid) {
        return BedrockLoginSkinCodec.readCanonicalSkin(in, uuid);
    }

    /** Mirror of the ≥2168 canonical Skin write path (byte arm_size, ARGB colour, varint arrays). */
    public static BedrockCanonicalSkin readCanonicalSkinV2168(ByteBuf in, UUID uuid) {
        return BedrockLoginSkinCodec.readCanonicalSkinV2168(in, uuid);
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
     * Geyser {@code UpstreamPacketHandler.handle(RequestNetworkSettings)}:
     * {@code CompressionAlgorithm.ZLIB} (ordinal 0) + threshold 512.
     * RakNet still prefixes method byte (0=deflate / 0xff=none) after this packet.
     */
    public static ByteBuf networkSettingsGeyser() {
        return networkSettings(512, 0, false, 0, 0f);
    }

    /**
     * @deprecated Prefer {@link #networkSettingsGeyser()} — matches Geyser zlib+512.
     * Kept as alias so callers that want "no force-compress" still get a valid ZLIB advert.
     */
    @Deprecated
    public static ByteBuf networkSettingsUncompressed() {
        return networkSettingsGeyser();
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

    /**
     * Player list add with one entry. Protocol-aware:
     * <ul>
     *   <li>≥2168 (Cloudburst/Geyser modern): per-entry action + locator colour + Skin v2168</li>
     *   <li>≥800: classic ADD list + locator colour + Skin 1.21.x + verified[]</li>
     *   <li>else: classic ADD list + Skin 1.21.x + verified[] (1.21.50/60)</li>
     * </ul>
     */
    public static ByteBuf playerListAddSelf(UUID uuid, long entityUniqueId, String username) {
        return playerListAddSelf(uuid, entityUniqueId, username, 776);
    }

    public static ByteBuf playerListAddSelf(UUID uuid, long entityUniqueId, String username, int protocol) {
        String name = username == null ? "Player" : username;
        ByteBuf out = Unpooled.buffer(512 + name.length());
        writeUnsignedVarInt(out, BedrockPacketIds.PLAYER_LIST.id);
        if (protocol >= 2168) {
            // PlayerListSerializer_v2168: entries[] each with action variant + payload
            writeUnsignedVarInt(out, 1);
            writeUnsignedVarInt(out, 1); // ADD variant
            out.writeByte(0); // Action.ADD ordinal
            writePlayerListEntryBody(out, uuid, entityUniqueId, name, protocol);
            return out;
        }
        out.writeByte(0); // type add
        writeUnsignedVarInt(out, 1); // records_count
        writePlayerListEntryBody(out, uuid, entityUniqueId, name, protocol);
        out.writeBoolean(true); // verified[0] (trusted skin) — trailing array for ADD
        return out;
    }

    private static void writePlayerListEntryBody(ByteBuf out, UUID uuid, long entityUniqueId,
                                                  String name, int protocol) {
        out.writeLongLE(uuid.getMostSignificantBits());
        out.writeLongLE(uuid.getLeastSignificantBits());
        writeZigZag64(out, entityUniqueId);
        writeString(out, name);
        writeString(out, ""); // xbox_user_id
        writeString(out, ""); // platform_chat_id
        out.writeIntLE(0); // build_platform (li32)
        if (protocol >= 2168) {
            writeMinimalSkinV2168(out);
        } else {
            writeMinimalSkin(out);
        }
        out.writeBoolean(false); // is_teacher
        out.writeBoolean(true); // is_host
        out.writeBoolean(false); // is_subclient
        if (protocol >= 800) {
            // PlayerListSerializer_v800 locator colour (ARGB LE)
            out.writeIntLE(0xFFFFFFFF);
        }
    }

    /** Minimal Skin matching 1.21.50–1.21.60 {@code Skin} + empty {@code SkinImage}s. */
    public static void writeMinimalSkin(ByteBuf out) {
        String geo = BedrockCanonicalSkin.loadParityGeometryJson("geometry.humanoid.custom");
        writeString(out, "Standard_Custom"); // skin_id
        writeString(out, ""); // play_fab_id
        writeString(out, "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}"); // skin_resource_pack
        BedrockLoginSkinImages.writeEmptySkinImage(out); // skin_data
        out.writeIntLE(0); // animations count (li32)
        BedrockLoginSkinImages.writeEmptySkinImage(out); // cape_data
        writeString(out, geo); // geometry_data (parity fixture — never empty when available)
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

    /**
     * Minimal Skin for proto ≥2168 (Cloudburst {@code BedrockCodecHelper_v2168}):
     * arm size u8, colour ARGB, piece counts as unsigned varints, trusted+profileHash strings.
     */
    public static void writeMinimalSkinV2168(ByteBuf out) {
        String geo = BedrockCanonicalSkin.loadParityGeometryJson("geometry.humanoid.custom");
        writeString(out, "Standard_Custom");
        writeString(out, ""); // play_fab_id
        writeString(out, "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}");
        BedrockLoginSkinImages.writeEmptySkinImage(out);
        writeUnsignedVarInt(out, 0); // animations
        BedrockLoginSkinImages.writeEmptySkinImage(out);
        writeString(out, geo); // geometry_data
        writeString(out, "1.14.0"); // geometry_data_version
        writeString(out, ""); // animation_data
        writeString(out, ""); // cape_id
        writeString(out, "Standard_Custom"); // full_skin_id
        out.writeByte(1); // arm_size: 0=slim, 1=wide
        out.writeIntLE(0); // colour ARGB (transparent black)
        writeUnsignedVarInt(out, 0); // persona pieces
        writeUnsignedVarInt(out, 0); // piece tint colours
        out.writeBoolean(false); // premium
        out.writeBoolean(false); // persona
        out.writeBoolean(false); // cape_on_classic
        out.writeBoolean(true); // primary_user
        out.writeBoolean(false); // overriding_player_appearance
        writeString(out, "true"); // trusted (string since v2168)
        writeString(out, ""); // profile_hash
    }

}

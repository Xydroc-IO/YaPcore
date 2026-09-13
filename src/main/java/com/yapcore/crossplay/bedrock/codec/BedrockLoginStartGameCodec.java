package com.yapcore.crossplay.bedrock.codec;

import com.yapcore.crossplay.bedrock.BedrockItemStates;
import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.bedrock.BedrockPacketIds;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import static com.yapcore.crossplay.bedrock.codec.BedrockCodecBinary.*;

/**
 * StartGame / ItemRegistry helpers (split from {@link BedrockLoginCodec} for the ≤500-line domain gate).
 */
final class BedrockLoginStartGameCodec {
    private BedrockLoginStartGameCodec() {}

    static ByteBuf startGame(long entityUniqueId, long runtimeId, String levelName,
                                    int blockX, int blockY, int blockZ, UUID worldId) {
        return startGame(entityUniqueId, runtimeId, levelName, blockX, blockY, blockZ, worldId, 2207);
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
    static ByteBuf startGame(long entityUniqueId, long runtimeId, String levelName,
                                    int blockX, int blockY, int blockZ, UUID worldId, int protocol) {
        // Hand-rolled path — CloudburstJoinCodec is used from BedrockLoginFlow.finishLogin.
        if (protocol >= 2168) {
            return startGameV2168(entityUniqueId, runtimeId, levelName, blockX, blockY, blockZ);
        }
        return startGameV776(entityUniqueId, runtimeId, levelName, blockX, blockY, blockZ);
    }

    /** Hand-rolled Cloudburst-aligned StartGameSerializer_v2168 (fallback when Cloudburst encode fails). */
    static ByteBuf startGameV2168(long entityUniqueId, long runtimeId, String levelName,
                                          int blockX, int blockY, int blockZ) {
        ByteBuf out = Unpooled.buffer(512);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_START_GAME);
        writeZigZag64(out, entityUniqueId);
        writeUnsignedVarLong(out, runtimeId);
        writeSignedVarInt(out, 0); // player_gamemode
        out.writeFloatLE(blockX + 0.5f);
        out.writeFloatLE(blockY + 1.62f);
        out.writeFloatLE(blockZ + 0.5f);
        out.writeFloatLE(0f);
        out.writeFloatLE(0f);
        out.writeLongLE(-1L); // seed
        out.writeShortLE(0); // biome_type
        writeString(out, "");
        writeSignedVarInt(out, 0); // dimension
        writeSignedVarInt(out, 1); // generator
        writeSignedVarInt(out, 0); // world_gamemode
        out.writeBoolean(false); // hardcore
        writeSignedVarInt(out, 1); // difficulty
        writeSignedVarInt(out, blockX);
        writeUnsignedVarInt(out, blockY);
        writeSignedVarInt(out, blockZ);
        out.writeBoolean(true); // achievements_disabled
        writeSignedVarInt(out, 0); // editor_world_type
        out.writeBoolean(false); // created_in_editor
        out.writeBoolean(false); // exported_from_editor
        writeSignedVarInt(out, -1); // day_cycle_stop_time
        writeUnsignedVarInt(out, 0); // edu_offer UNSIGNED (v2168)
        out.writeBoolean(false);
        writeString(out, "");
        out.writeFloatLE(0f);
        out.writeFloatLE(0f);
        out.writeBoolean(false);
        out.writeBoolean(true); // multiplayer
        out.writeBoolean(true); // lan
        writeSignedVarInt(out, 4);
        writeSignedVarInt(out, 4);
        out.writeBoolean(true); // commands
        out.writeBoolean(false); // texturepacks_required
        writeUnsignedVarInt(out, 0); // gamerules
        // Empty experiments — enabling data_driven_items without matching palettes crashes 1.26/2207.
        out.writeIntLE(0);
        out.writeBoolean(false); // experiments_previously_used
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeByte(1); // permission MEMBER as BYTE (v2168)
        out.writeIntLE(4);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        writeString(out, "*");
        out.writeIntLE(0);
        out.writeIntLE(0);
        out.writeBoolean(false);
        writeString(out, "");
        writeString(out, "");
        out.writeBoolean(false); // force_experimental optional absent
        out.writeByte(0); // chat_restriction
        out.writeBoolean(false); // disable_player_interactions
        writeSignedVarInt(out, 0); // server_editor_connection_policy (v1001+)
        out.writeBoolean(false); // allow_anonymous_block_drops
        // telemetry NOT in level settings for ≥924 — at packet tail
        String name = levelName == null ? "YaPcore" : levelName;
        writeString(out, name);
        writeString(out, name);
        writeString(out, "00000000-0000-0000-0000-000000000000");
        out.writeBoolean(false); // trial
        // PlayerMovementSettings ≥818: rewind + server-auth breaking (no movement mode)
        writeSignedVarInt(out, 0);
        out.writeBoolean(true);
        out.writeLongLE(-1L);
        writeSignedVarInt(out, 0);
        writeUnsignedVarInt(out, 0); // block_properties
        // itemDefinitions noop ≥776
        writeString(out, "");
        out.writeBoolean(true); // server_authoritative_inventory
        writeString(out, ""); // serverEngine
        writeEmptyNetworkNbt(out);
        out.writeLongLE(0L);
        UUID worldTemplate = UUID.randomUUID();
        out.writeLongLE(worldTemplate.getMostSignificantBits());
        out.writeLongLE(worldTemplate.getLeastSignificantBits());
        out.writeBoolean(false); // client_side_generation
        out.writeBoolean(true); // block_network_ids_are_hashes
        out.writeBoolean(false); // NetworkPermissions.serverAuthSounds (isLoggingChat removed ≥2168)
        out.writeBoolean(false); // ServerJoinInfo optional absent
        writeString(out, ""); // ServerID
        writeString(out, ""); // ScenarioID
        writeString(out, ""); // WorldID
        writeString(out, ""); // OwnerID
        return out;
    }

    /**
     * Cloudburst StartGameSerializer_v776: itemDefinitions noop; level settings from v685
     * (ServerId/WorldId/ScenarioId after interactions); movement still 3 fields (pre-818).
     */
    static ByteBuf startGameV776(long entityUniqueId, long runtimeId, String levelName,
                                         int blockX, int blockY, int blockZ) {
        ByteBuf out = Unpooled.buffer(512);
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_START_GAME);
        writeZigZag64(out, entityUniqueId);
        writeUnsignedVarLong(out, runtimeId);
        writeSignedVarInt(out, 0);
        out.writeFloatLE(blockX + 0.5f);
        out.writeFloatLE(blockY + 1.62f);
        out.writeFloatLE(blockZ + 0.5f);
        out.writeFloatLE(0f);
        out.writeFloatLE(0f);
        out.writeLongLE(-1L);
        out.writeShortLE(0);
        writeString(out, "");
        writeSignedVarInt(out, 0);
        writeSignedVarInt(out, 1);
        writeSignedVarInt(out, 0);
        out.writeBoolean(false);
        writeSignedVarInt(out, 1);
        writeSignedVarInt(out, blockX);
        writeUnsignedVarInt(out, blockY);
        writeSignedVarInt(out, blockZ);
        out.writeBoolean(true);
        writeSignedVarInt(out, 0); // editor_world_type
        out.writeBoolean(false);
        out.writeBoolean(false);
        writeSignedVarInt(out, -1);
        writeSignedVarInt(out, 0); // edu_offer SIGNED (pre-2168)
        out.writeBoolean(false);
        writeString(out, "");
        out.writeFloatLE(0f);
        out.writeFloatLE(0f);
        out.writeBoolean(false);
        out.writeBoolean(true);
        out.writeBoolean(true);
        writeSignedVarInt(out, 4);
        writeSignedVarInt(out, 4);
        out.writeBoolean(true);
        out.writeBoolean(false);
        writeUnsignedVarInt(out, 0);
        out.writeIntLE(0); // experiments empty for legacy path
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        writeSignedVarInt(out, 1); // permission MEMBER as VARINT (pre-2168)
        out.writeIntLE(4);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        writeString(out, "*");
        out.writeIntLE(0);
        out.writeIntLE(0);
        out.writeBoolean(false);
        writeString(out, "");
        writeString(out, "");
        out.writeBoolean(false);
        out.writeByte(0);
        out.writeBoolean(false);
        // v685 level-settings telemetry (NOT at packet tail)
        writeString(out, ""); // ServerId
        writeString(out, ""); // WorldId
        writeString(out, ""); // ScenarioId
        String name = levelName == null ? "YaPcore" : levelName;
        writeString(out, name);
        writeString(out, name);
        writeString(out, "00000000-0000-0000-0000-000000000000");
        out.writeBoolean(false);
        // PlayerMovementSettings pre-818: mode + rewind + breaking
        writeSignedVarInt(out, 0); // authoritative movement mode
        writeSignedVarInt(out, 0); // rewind
        out.writeBoolean(true);
        out.writeLongLE(-1L);
        writeSignedVarInt(out, 0);
        writeUnsignedVarInt(out, 0);
        // itemDefinitions noop
        writeString(out, "");
        out.writeBoolean(true);
        writeString(out, "");
        writeEmptyNetworkNbt(out);
        out.writeLongLE(0L);
        UUID worldTemplate = UUID.randomUUID();
        out.writeLongLE(worldTemplate.getMostSignificantBits());
        out.writeLongLE(worldTemplate.getLeastSignificantBits());
        out.writeBoolean(false);
        out.writeBoolean(true);
        out.writeBoolean(false); // NetworkPermissions only (no tickDeath / loggingChat on plain 776)
        return out;
    }

    /**
     * ItemRegistry / ItemComponent (Cloudburst id 162 = 0xa2) — replaces StartGame itemstates for ≥776.
     * Entry layout: ItemComponentSerializer_v776 — name, shortLE runtimeId, componentBased, version varint, NBT.
     */
    static ByteBuf itemRegistry() {
        return itemRegistry(2207);
    }

    /**
     * ItemComponent (0xa2). Cloudburst {@code ItemComponentSerializer_v776} entry layout is correct,
     * but our dump is protocol/bedrock/1.21.50 — wrong runtime ids for 1.26.x / proto 2207 and hard-crashes.
     * Empty registry lets the client use its built-in palette (connect first; per-proto dumps later).
     */
    static ByteBuf itemRegistry(int protocol) {
        // Empty for ≥2168 (built-in client palette). CloudburstJoinCodec preferred in finishLogin.
        if (protocol >= 2168) {
            return itemComponentEmptyHandRolled();
        }
        List<BedrockItemStates.ItemState> states = BedrockItemStates.all();
        ByteBuf out = Unpooled.buffer(Math.max(64, states.size() * 24));
        writeUnsignedVarInt(out, BedrockPacketIds.ITEM_COMPONENT.id);
        writeUnsignedVarInt(out, states.size());
        for (BedrockItemStates.ItemState s : states) {
            writeString(out, s.name());
            out.writeShortLE(s.runtimeId());
            out.writeBoolean(s.componentBased());
            writeSignedVarInt(out, 0);
            writeEmptyNetworkNbt(out);
        }
        return out;
    }

    /** Empty ItemComponent (0xa2) — hand-rolled fallback. */
    static ByteBuf itemComponentEmptyHandRolled() {
        ByteBuf out = Unpooled.buffer(8);
        writeUnsignedVarInt(out, BedrockPacketIds.ITEM_COMPONENT.id);
        writeUnsignedVarInt(out, 0);
        return out;
    }
}

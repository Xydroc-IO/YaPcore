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

public final class BedrockEntityCodec {
    private BedrockEntityCodec() {}
    /** Full ADD_PLAYER matching 1.21.50 {@code packet_add_player}. */
    public static ByteBuf addPlayer(UUID uuid, String username, long runtimeId,
                                    float x, float y, float z, float yaw, float pitch) {
        ByteBuf out = Unpooled.buffer(192 + (username == null ? 0 : username.length()));
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_ADD_PLAYER);
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
        float[] aabb = BedrockActorAabb.forActor(actorTypeOrNametag);
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
        float[] aabb = BedrockActorAabb.forActor(actorType);
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
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_REMOVE_ENTITY);
        out.writeLongLE(uniqueEntityId);
        return out;
    }
}

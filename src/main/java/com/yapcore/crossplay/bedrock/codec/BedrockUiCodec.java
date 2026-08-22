package com.yapcore.crossplay.bedrock.codec;

import com.yapcore.crossplay.bedrock.BedrockPacketIds;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.UUID;

import static com.yapcore.crossplay.bedrock.codec.BedrockCodecBinary.*;

/**
 * Bedrock title, boss bar, and scoreboard packets (G.31 parity).
 */
public final class BedrockUiCodec {

    public static final int TITLE_CLEAR = 0;
    public static final int TITLE_RESET = 1;
    public static final int TITLE_SET = 2;
    public static final int TITLE_SUBTITLE = 3;
    public static final int TITLE_ACTIONBAR = 4;
    public static final int TITLE_TIMES = 5;

    public static final int BOSS_SHOW = 0;
    public static final int BOSS_HIDE = 2;
    public static final int BOSS_HEALTH = 4;
    public static final int BOSS_TITLE = 5;

    public static final int SCORE_TYPE_PLAYER = 1;
    public static final int SCORE_TYPE_FAKE = 3;

    private BedrockUiCodec() {}

    public static ByteBuf setTitle(int type, String text, int fadeIn, int stay, int fadeOut) {
        ByteBuf out = Unpooled.buffer(64 + (text == null ? 0 : text.length()));
        writeUnsignedVarInt(out, BedrockPacketIds.SET_TITLE.id);
        writeSignedVarInt(out, type);
        writeString(out, text == null ? "" : text);
        writeSignedVarInt(out, fadeIn);
        writeSignedVarInt(out, stay);
        writeSignedVarInt(out, fadeOut);
        writeString(out, "");
        writeString(out, "");
        writeString(out, text == null ? "" : text);
        return out;
    }

    public static ByteBuf bossEvent(long bossActorId, int eventType, String title, float healthPercent,
                                    int color, int overlay) {
        ByteBuf out = Unpooled.buffer(96 + (title == null ? 0 : title.length()));
        writeUnsignedVarInt(out, BedrockPacketIds.BOSS_EVENT.id);
        writeZigZag64(out, bossActorId);
        writeZigZag64(out, 0L);
        out.writeByte(eventType & 0xFF);
        writeString(out, title == null ? "" : title);
        writeString(out, title == null ? "" : title);
        out.writeFloatLE(healthPercent);
        out.writeByte(color & 0xFF);
        out.writeByte(overlay & 0xFF);
        return out;
    }

    public static ByteBuf setDisplayObjective(String slot, String objectiveId, String displayName,
                                               String criteria, int sortOrder) {
        ByteBuf out = Unpooled.buffer(96);
        writeUnsignedVarInt(out, BedrockPacketIds.SET_DISPLAY_OBJECTIVE.id);
        writeString(out, slot == null ? "sidebar" : slot);
        writeString(out, objectiveId == null ? "yap" : objectiveId);
        writeString(out, displayName == null ? objectiveId : displayName);
        writeString(out, criteria == null ? "dummy" : criteria);
        writeSignedVarInt(out, sortOrder);
        return out;
    }

    public static ByteBuf setScore(int action, long scoreboardId, String objective, int score,
                                   int entryType, long actorUniqueId, String fakeName) {
        ByteBuf out = Unpooled.buffer(96);
        writeUnsignedVarInt(out, BedrockPacketIds.SET_SCORE.id);
        out.writeByte(action & 0xFF);
        writeUnsignedVarInt(out, 1);
        writeSignedVarLong(out, scoreboardId);
        writeString(out, objective == null ? "yap" : objective);
        out.writeIntLE(score);
        if (action == 0) {
            out.writeByte(entryType & 0xFF);
            if (entryType == SCORE_TYPE_PLAYER || entryType == 2) {
                writeZigZag64(out, actorUniqueId);
            } else if (entryType == SCORE_TYPE_FAKE) {
                writeString(out, fakeName == null ? "" : fakeName);
            }
        }
        return out;
    }

    /** Player-head block actor: skull type + owner name for BE clients (G.33 best-effort). */
    public static ByteBuf blockActorSkull(int x, int y, int z, String ownerName) {
        ByteBuf out = Unpooled.buffer(128);
        writeUnsignedVarInt(out, BedrockPacketIds.BLOCK_ACTOR_DATA.id);
        writeBlockPosition(out, x, y, z);
        // Minimal network NBT: SkullType=3 (player), ExtraData=owner
        String tag = ownerName == null ? "" : ownerName.replace("\"", "");
        writeString(out, "{"
                + "\"id\":\"Skull\","
                + "\"SkullType\":3,"
                + "\"ExtraData\":\"" + tag + "\""
                + "}");
        return out;
    }

    public static UUID parsePackUuid(String packId) {
        if (packId == null || packId.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(packId);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(packId.getBytes());
        }
    }
}

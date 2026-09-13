package com.yapcore.link.bedrock.downstream;

import com.yapcore.protocol.McCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Paper 26.2 / protocol 776 play packet IDs and best-effort C2S encoders for Link-native Bedrock.
 *
 * <p>IDs from {@code protocol/vanilla/26.2/packets.json}. Layouts are simplified where the full
 * JE codec is not yet ported — enough for movement, dig/place, chat, and held-item smoke.
 */
public final class JavaPlayWire {

    // Clientbound
    public static final int CB_ADD_ENTITY = 1;
    public static final int CB_BLOCK_UPDATE = 8;
    /** JE {@code commands} / declare_commands — Brigadier tree. */
    public static final int CB_COMMANDS = 16;
    public static final int CB_CONTAINER_SET_CONTENT = 18;
    public static final int CB_CONTAINER_SET_SLOT = 20;
    public static final int CB_DISCONNECT = 32;
    public static final int CB_ENTITY_POSITION_SYNC = 35;
    public static final int CB_HURT_ANIMATION = 42;
    public static final int CB_KEEP_ALIVE = 44;
    public static final int CB_LEVEL_CHUNK = 45;
    public static final int CB_LEVEL_EVENT = 46;
    public static final int CB_LOGIN = 49;
    public static final int CB_MOVE_ENTITY_POS = 53;
    public static final int CB_MOVE_ENTITY_POS_ROT = 54;
    public static final int CB_MOVE_ENTITY_ROT = 56;
    public static final int CB_OPEN_SCREEN = 59;
    public static final int CB_PING = 61;
    public static final int CB_PLAYER_CHAT = 65;
    /** JE {@code player_remove} (tab-list remove). */
    public static final int CB_PLAYER_REMOVE = 69;
    /** JE {@code player_info} / {@code player_info_update}. */
    public static final int CB_PLAYER_INFO = 70;
    public static final int CB_PLAYER_POSITION = 72;
    public static final int CB_REMOVE_ENTITIES = 77;
    public static final int CB_SECTION_BLOCKS_UPDATE = 84;
    public static final int CB_SET_ENTITY_DATA = 99;
    /** JE {@code set_health} — health f32 + food varint + saturation f32. */
    public static final int CB_SET_HEALTH = 104;
    public static final int CB_SOUND = 117;
    public static final int CB_SOUND_ENTITY = 116;
    public static final int CB_SYSTEM_CHAT = 121;
    public static final int CB_TELEPORT_ENTITY = 125;
    /** Clientbound entity_event — status 24–28 = op permission level 0–4. */
    public static final int CB_ENTITY_EVENT = 34;
    /** JE {@code player_combat_kill} — death screen message. */
    public static final int CB_PLAYER_COMBAT_KILL = 68;

    // Serverbound
    public static final int SB_ACCEPT_TELEPORT = 0;
    /** Proto 26.2+ {@code minecraft:attack} — entityId only (replaces interact type=attack). */
    public static final int SB_ATTACK = 1;
    public static final int SB_CHAT_COMMAND = 7;
    public static final int SB_CHAT = 9;
    /** {@code minecraft:client_command} — PERFORM_RESPAWN=0. */
    public static final int SB_CLIENT_COMMAND = 12;
    /** {@code minecraft:client_tick_end} — required each tick on proto 776+ (Geyser sends after auth). */
    public static final int SB_CLIENT_TICK_END = 13;
    public static final int SB_CLIENT_INFORMATION = 14;
    public static final int SB_INTERACT = 26;
    public static final int SB_KEEP_ALIVE = 28;
    public static final int SB_MOVE_POS = 30;
    public static final int SB_MOVE_POS_ROT = 31;
    public static final int SB_MOVE_ROT = 32;
    public static final int SB_MOVE_STATUS = 33;
    public static final int SB_PLAYER_ACTION = 41;
    /** {@code minecraft:player_input} — bitflags; required before moves on proto 776+ (Geyser InputCache). */
    public static final int SB_PLAYER_INPUT = 43;
    /** {@code minecraft:player_loaded} — Folia waits for this before chunk stream (proto 776). */
    public static final int SB_PLAYER_LOADED = 44;
    public static final int SB_SET_CARRIED_ITEM = 53;
    public static final int SB_SWING = 63;
    public static final int SB_USE_ITEM_ON = 66;
    public static final int SB_USE_ITEM = 67;

    public static final int ACTION_START_DIG = 0;
    public static final int ACTION_ABORT_DIG = 1;
    public static final int ACTION_STOP_DIG = 2;
    public static final int ACTION_DROP_ALL = 3;
    public static final int ACTION_DROP_ITEM = 4;
    public static final int ACTION_RELEASE_USE = 5;
    public static final int ACTION_SWAP_OFFHAND = 6;

    private JavaPlayWire() {
    }

    public static ByteBuf playerLoaded() {
        ByteBuf buf = Unpooled.buffer(4);
        McCodec.writeVarInt(buf, SB_PLAYER_LOADED);
        return buf;
    }

    public static ByteBuf acceptTeleport(int teleportId) {
        ByteBuf buf = Unpooled.buffer(8);
        McCodec.writeVarInt(buf, SB_ACCEPT_TELEPORT);
        McCodec.writeVarInt(buf, teleportId);
        return buf;
    }

    /** Proto 776 packs onGround (bit0) + horizontalCollision (bit1) into one unsigned byte. */
    private static int packMoveFlags(boolean onGround, boolean horizontalCollision) {
        int flags = 0;
        if (onGround) {
            flags |= 1;
        }
        if (horizontalCollision) {
            flags |= 2;
        }
        return flags;
    }

    public static ByteBuf clientTickEnd() {
        ByteBuf buf = Unpooled.buffer(2);
        McCodec.writeVarInt(buf, SB_CLIENT_TICK_END);
        return buf;
    }

    /**
     * Proto 776+ {@code minecraft:player_input}: one flags byte
     * (forward|backward|left|right|jump|shift|sprint = 1|2|4|8|16|32|64).
     * Geyser sends this <em>before</em> position packets each AuthInput.
     */
    public static ByteBuf playerInput(boolean forward, boolean backward, boolean left, boolean right,
                                      boolean jump, boolean shift, boolean sprint) {
        ByteBuf buf = Unpooled.buffer(4);
        McCodec.writeVarInt(buf, SB_PLAYER_INPUT);
        int flags = 0;
        if (forward) {
            flags |= 1;
        }
        if (backward) {
            flags |= 2;
        }
        if (left) {
            flags |= 4;
        }
        if (right) {
            flags |= 8;
        }
        if (jump) {
            flags |= 16;
        }
        if (shift) {
            flags |= 32;
        }
        if (sprint) {
            flags |= 64;
        }
        buf.writeByte(flags);
        return buf;
    }

    public static ByteBuf movePosRot(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        return movePosRot(x, y, z, yaw, pitch, onGround, false);
    }

    public static ByteBuf movePosRot(double x, double y, double z, float yaw, float pitch,
                                     boolean onGround, boolean horizontalCollision) {
        ByteBuf buf = Unpooled.buffer(40);
        McCodec.writeVarInt(buf, SB_MOVE_POS_ROT);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeByte(packMoveFlags(onGround, horizontalCollision));
        return buf;
    }

    public static ByteBuf movePos(double x, double y, double z, boolean onGround) {
        return movePos(x, y, z, onGround, false);
    }

    public static ByteBuf movePos(double x, double y, double z, boolean onGround, boolean horizontalCollision) {
        ByteBuf buf = Unpooled.buffer(32);
        McCodec.writeVarInt(buf, SB_MOVE_POS);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeByte(packMoveFlags(onGround, horizontalCollision));
        return buf;
    }

    public static ByteBuf setCarriedItem(int hotbarSlot) {
        ByteBuf buf = Unpooled.buffer(8);
        McCodec.writeVarInt(buf, SB_SET_CARRIED_ITEM);
        buf.writeShort(Math.max(0, Math.min(8, hotbarSlot)));
        return buf;
    }

    public static ByteBuf playerAction(int status, int x, int y, int z, int face, int sequence) {
        ByteBuf buf = Unpooled.buffer(32);
        McCodec.writeVarInt(buf, SB_PLAYER_ACTION);
        McCodec.writeVarInt(buf, status);
        buf.writeLong(packBlockPos(x, y, z));
        buf.writeByte(face & 0xff);
        McCodec.writeVarInt(buf, Math.max(0, sequence));
        return buf;
    }

    public static ByteBuf useItemOn(int x, int y, int z, int face,
                                   float cursorX, float cursorY, float cursorZ,
                                   boolean insideBlock, int hand, int sequence) {
        ByteBuf buf = Unpooled.buffer(48);
        McCodec.writeVarInt(buf, SB_USE_ITEM_ON);
        McCodec.writeVarInt(buf, hand); // 0 main
        buf.writeLong(packBlockPos(x, y, z));
        McCodec.writeVarInt(buf, face);
        buf.writeFloat(cursorX);
        buf.writeFloat(cursorY);
        buf.writeFloat(cursorZ);
        buf.writeBoolean(insideBlock);
        buf.writeBoolean(false); // worldBorderHit (1.21+)
        McCodec.writeVarInt(buf, Math.max(0, sequence));
        return buf;
    }

    /**
     * Proto 26.2 {@code minecraft:attack}: varint entityId only.
     * Legacy interact type=1 kicked Folia with DecoderException (probe 14:16 / 14:34).
     */
    public static ByteBuf interactAttack(int entityId, boolean sneaking) {
        ByteBuf buf = Unpooled.buffer(8);
        McCodec.writeVarInt(buf, SB_ATTACK);
        McCodec.writeVarInt(buf, entityId);
        return buf;
    }

    /** {@code minecraft:client_command} action 0 = PERFORM_RESPAWN. */
    public static ByteBuf clientCommandRespawn() {
        ByteBuf buf = Unpooled.buffer(4);
        McCodec.writeVarInt(buf, SB_CLIENT_COMMAND);
        McCodec.writeVarInt(buf, 0); // PERFORM_RESPAWN
        return buf;
    }

    /** Hex dump helper for attack wire debugging (packet id + entityId). */
    public static String attackWireHex(int entityId) {
        ByteBuf buf = interactAttack(entityId, false);
        try {
            StringBuilder sb = new StringBuilder(buf.readableBytes() * 2);
            while (buf.isReadable()) {
                sb.append(String.format("%02x", buf.readUnsignedByte()));
            }
            return sb.toString();
        } finally {
            buf.release();
        }
    }

    /** JE interact-at / use-entity (hand + location + secondary). Not used for attacks. */
    public static ByteBuf interactAt(int entityId, int hand, float x, float y, float z,
                                     boolean secondary) {
        ByteBuf buf = Unpooled.buffer(32);
        McCodec.writeVarInt(buf, SB_INTERACT);
        McCodec.writeVarInt(buf, entityId);
        McCodec.writeVarInt(buf, Math.max(0, hand));
        // Vec3.LP_STREAM_CODEC — quantized; write as 3 floats for best-effort reach checks.
        buf.writeFloat(x);
        buf.writeFloat(y);
        buf.writeFloat(z);
        buf.writeBoolean(secondary);
        return buf;
    }

    public static ByteBuf swingArm(int hand) {
        ByteBuf buf = Unpooled.buffer(8);
        McCodec.writeVarInt(buf, SB_SWING);
        McCodec.writeVarInt(buf, hand);
        return buf;
    }

    /**
     * Unsigned {@code minecraft:chat_command} (proto id 7) — command string only.
     * Signed fields belong on id 8 ({@code chat_command_signed}); writing them here caused
     * Folia DecoderException "found 22 bytes extra" and kicked Bedrock command users.
     */
    public static ByteBuf chatCommand(String commandWithoutSlash) {
        String cmd = commandWithoutSlash == null ? "" : commandWithoutSlash.trim();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        if (cmd.length() > 256) {
            cmd = cmd.substring(0, 256);
        }
        ByteBuf buf = Unpooled.buffer(8 + cmd.length());
        McCodec.writeVarInt(buf, SB_CHAT_COMMAND);
        McCodec.writeString(buf, cmd);
        return buf;
    }

    /** Unsigned chat message. Best-effort for offline/Floodgate backends. */
    public static ByteBuf chatMessage(String message) {
        String msg = message == null ? "" : message;
        if (msg.length() > 256) {
            msg = msg.substring(0, 256);
        }
        ByteBuf buf = Unpooled.buffer(64 + msg.length());
        McCodec.writeVarInt(buf, SB_CHAT);
        McCodec.writeString(buf, msg);
        buf.writeLong(System.currentTimeMillis());
        buf.writeLong(0L);
        buf.writeBoolean(false); // no signature
        McCodec.writeVarInt(buf, 0);
        buf.writeBytes(new byte[3]);
        buf.writeByte(0);
        return buf;
    }

    public static long packBlockPos(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }

    public static int unpackBlockX(long pos) {
        return (int) (pos >> 38);
    }

    public static int unpackBlockY(long pos) {
        int y = (int) (pos << 52 >> 52);
        return y;
    }

    public static int unpackBlockZ(long pos) {
        return (int) (pos << 26 >> 38);
    }
}

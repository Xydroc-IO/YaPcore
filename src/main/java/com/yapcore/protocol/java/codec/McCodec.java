package com.yapcore.protocol.java.codec;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Minecraft protocol primitives (VarInt, strings, UUID). */
public final class McCodec {

    private McCodec() {
    }

    public static int readVarInt(ByteBuf buf) {
        int value = 0;
        int position = 0;
        byte current;
        while (true) {
            current = buf.readByte();
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                break;
            }
            position += 7;
            if (position >= 32) {
                throw new IllegalArgumentException("VarInt too big");
            }
        }
        return value;
    }

    public static void writeVarInt(ByteBuf buf, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                buf.writeByte(value);
                return;
            }
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    public static int varIntBytes(int value) {
        int count = 0;
        while (true) {
            count++;
            if ((value & ~0x7F) == 0) {
                return count;
            }
            value >>>= 7;
        }
    }

    public static String readString(ByteBuf buf, int max) {
        int len = readVarInt(buf);
        if (len < 0 || len > max * 3) {
            throw new IllegalArgumentException("Bad string length " + len);
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        String s = new String(bytes, StandardCharsets.UTF_8);
        if (s.length() > max) {
            throw new IllegalArgumentException("String too long");
        }
        return s;
    }

    public static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    public static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static void writeIdentifier(ByteBuf buf, String id) {
        writeString(buf, id);
    }

    /** Offline-mode UUID (vanilla OfflinePlayer:name). */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}

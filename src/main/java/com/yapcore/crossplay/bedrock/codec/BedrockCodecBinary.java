package com.yapcore.crossplay.bedrock.codec;
import io.netty.buffer.ByteBuf; import java.nio.charset.StandardCharsets;
public final class BedrockCodecBinary { private BedrockCodecBinary() {}    public static void writeUnsignedVarInt(ByteBuf out, int value) {
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

}

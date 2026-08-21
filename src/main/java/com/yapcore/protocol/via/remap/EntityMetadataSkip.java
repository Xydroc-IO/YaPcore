package com.yapcore.protocol.via.remap;

import com.yapcore.protocol.java.ProtocolBand;
import com.yapcore.protocol.java.codec.McCodec;
import io.netty.buffer.ByteBuf;

/**
 * Skip (and optionally copy) entity_metadata value payloads by serializer type id.
 * item_stack (7) is handled by the caller. Particle / exotic types fail-soft (throw).
 */
public final class EntityMetadataSkip {

    private EntityMetadataSkip() {
    }

    /** @return false if type cannot be skipped safely */
    public static boolean skipValue(ProtocolBand band, int type, ByteBuf buf) {
        try {
            if (band.ordinal() <= ProtocolBand.V1_20_2.ordinal()) {
                return skip765(type, buf);
            }
            return skipModern(type, buf);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean copyValue(ProtocolBand band, int type, ByteBuf in, ByteBuf out) {
        int mark = in.readerIndex();
        if (!skipValue(band, type, in)) {
            in.readerIndex(mark);
            return false;
        }
        out.writeBytes(in, mark, in.readerIndex() - mark);
        return true;
    }

    private static boolean skip765(int type, ByteBuf buf) {
        return switch (type) {
            case 0 -> { buf.readByte(); yield true; } // byte
            case 1 -> { McCodec.readVarInt(buf); yield true; } // int
            case 2 -> { readVarLong(buf); yield true; } // long
            case 3 -> { buf.readFloat(); yield true; }
            case 4 -> { McCodec.readString(buf, 32767); yield true; }
            case 5 -> { skipAnonymousNbt(buf); yield true; } // component
            case 6 -> { // optional component
                if (buf.readBoolean()) {
                    skipAnonymousNbt(buf);
                }
                yield true;
            }
            case 7 -> false; // item — caller
            case 8 -> { buf.readBoolean(); yield true; }
            case 9 -> { buf.skipBytes(12); yield true; } // rotations 3xf32
            case 10 -> { buf.readLong(); yield true; } // block_pos
            case 11 -> {
                if (buf.readBoolean()) {
                    buf.readLong();
                }
                yield true;
            }
            case 12, 14, 18, 19, 20, 21, 22, 24, 25 -> { McCodec.readVarInt(buf); yield true; }
            case 13 -> {
                if (buf.readBoolean()) {
                    buf.skipBytes(16);
                }
                yield true;
            }
            case 15 -> { // optional block state (opt varint)
                if (buf.readBoolean()) {
                    McCodec.readVarInt(buf);
                }
                yield true;
            }
            case 16 -> { skipAnonymousNbt(buf); yield true; } // compound_tag
            case 17 -> ParticleMetadataSkip.skipParticle(ProtocolBand.V1_20_2, buf);
            case 23 -> {
                if (buf.readBoolean()) {
                    McCodec.readString(buf, 32767);
                    buf.readLong();
                }
                yield true;
            }
            case 26 -> { buf.skipBytes(12); yield true; } // vector3
            case 27 -> { buf.skipBytes(16); yield true; } // quaternion
            default -> false;
        };
    }

    /** 1.21.x / 26.x serializer ids (item_stack still 7). */
    private static boolean skipModern(int type, ByteBuf buf) {
        return switch (type) {
            case 0 -> { buf.readByte(); yield true; }
            case 1 -> { McCodec.readVarInt(buf); yield true; }
            case 2 -> { readVarLong(buf); yield true; }
            case 3 -> { buf.readFloat(); yield true; }
            case 4 -> { McCodec.readString(buf, 32767); yield true; }
            case 5 -> { skipAnonymousNbt(buf); yield true; }
            case 6 -> {
                if (buf.readBoolean()) {
                    skipAnonymousNbt(buf);
                }
                yield true;
            }
            case 7 -> false;
            case 8 -> { buf.readBoolean(); yield true; }
            case 9 -> { buf.skipBytes(12); yield true; }
            case 10 -> { buf.readLong(); yield true; }
            case 11 -> {
                if (buf.readBoolean()) {
                    buf.readLong();
                }
                yield true;
            }
            case 12, 14, 18, 19, 20 -> { McCodec.readVarInt(buf); yield true; }
            case 13 -> {
                if (buf.readBoolean()) {
                    buf.skipBytes(16);
                }
                yield true;
            }
            case 15 -> {
                if (buf.readBoolean()) {
                    McCodec.readVarInt(buf);
                }
                yield true;
            }
            case 16 -> ParticleMetadataSkip.skipParticle(ProtocolBand.V1_21, buf);
            case 17 -> ParticleMetadataSkip.skipParticles(ProtocolBand.V1_21, buf);
            default -> {
                // Most later variant ids are varint (cat, frog, painting registry, pose siblings)
                if (type >= 18 && type <= 38) {
                    McCodec.readVarInt(buf);
                    yield true;
                }
                if (type == 39) {
                    buf.skipBytes(12);
                    yield true;
                }
                if (type == 40) {
                    buf.skipBytes(16);
                    yield true;
                }
                yield false;
            }
        };
    }

    private static void skipAnonymousNbt(ByteBuf buf) {
        byte type = buf.readByte();
        if (type == 0) {
            return;
        }
        skipNbtPayload(buf, type);
    }

    private static long readVarLong(ByteBuf buf) {
        long value = 0;
        int position = 0;
        while (true) {
            byte current = buf.readByte();
            value |= (long) (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                break;
            }
            position += 7;
            if (position >= 64) {
                throw new IllegalArgumentException("VarLong too big");
            }
        }
        return value;
    }

    private static void skipNbtPayload(ByteBuf buf, byte type) {
        switch (type) {
            case 0x00 -> {
            }
            case 0x01 -> buf.skipBytes(1);
            case 0x02 -> buf.skipBytes(2);
            case 0x03, 0x05 -> buf.skipBytes(4);
            case 0x04, 0x06 -> buf.skipBytes(8);
            case 0x07 -> {
                int len = buf.readInt();
                buf.skipBytes(Math.max(0, len));
            }
            case 0x08 -> {
                int len = buf.readUnsignedShort();
                buf.skipBytes(len);
            }
            case 0x09 -> {
                byte elem = buf.readByte();
                int len = buf.readInt();
                for (int i = 0; i < len; i++) {
                    skipNbtPayload(buf, elem);
                }
            }
            case 0x0a -> {
                while (buf.isReadable()) {
                    byte t = buf.readByte();
                    if (t == 0x00) {
                        break;
                    }
                    int nameLen = buf.readUnsignedShort();
                    buf.skipBytes(nameLen);
                    skipNbtPayload(buf, t);
                }
            }
            case 0x0b -> {
                int len = buf.readInt();
                buf.skipBytes(len * 4);
            }
            case 0x0c -> {
                int len = buf.readInt();
                buf.skipBytes(len * 8);
            }
            default -> {
            }
        }
    }
}

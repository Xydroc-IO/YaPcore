package com.yapcore.crossplay.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Minimal RakNet unconnected ping/pong (Geyser parity foundation 4.G1).
 * Magic: 00ffff00fefefefefdfdfdfd12345678
 */
public final class RakNetUnconnected {

    public static final byte[] MAGIC = new byte[]{
            0x00, (byte) 0xff, (byte) 0xff, 0x00,
            (byte) 0xfe, (byte) 0xfe, (byte) 0xfe, (byte) 0xfe,
            (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd,
            0x12, 0x34, 0x56, 0x78
    };

    public static final int ID_UNCONNECTED_PING = 0x01;
    public static final int ID_UNCONNECTED_PING_OPEN = 0x02;
    public static final int ID_UNCONNECTED_PONG = 0x1c;

    private RakNetUnconnected() {
    }

    public static boolean isUnconnectedPing(ByteBuf buf) {
        if (buf.readableBytes() < 1) {
            return false;
        }
        int id = buf.getUnsignedByte(buf.readerIndex());
        return id == ID_UNCONNECTED_PING || id == ID_UNCONNECTED_PING_OPEN;
    }

    public static ByteBuf buildPong(long pingTime, long guid, String motd) {
        byte[] motdBytes = motd.getBytes(StandardCharsets.UTF_8);
        ByteBuf buf = Unpooled.buffer(1 + 8 + 8 + MAGIC.length + 2 + motdBytes.length);
        buf.writeByte(ID_UNCONNECTED_PONG);
        buf.writeLong(pingTime);
        buf.writeLong(guid);
        buf.writeBytes(MAGIC);
        buf.writeShort(motdBytes.length);
        buf.writeBytes(motdBytes);
        return buf;
    }

    public static long readPingTime(ByteBuf buf) {
        int idx = buf.readerIndex();
        buf.readUnsignedByte(); // id
        long time = buf.readLong();
        buf.readerIndex(idx);
        return time;
    }

    public static boolean magicMatches(ByteBuf buf, int magicOffset) {
        if (buf.readableBytes() < magicOffset + MAGIC.length) {
            return false;
        }
        byte[] got = new byte[MAGIC.length];
        buf.getBytes(buf.readerIndex() + magicOffset, got);
        return Arrays.equals(got, MAGIC);
    }
}

package com.yapcore.link.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Minimal RakNet unconnected ping/pong (Geyser / BDS parity).
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
        if (buf.readableBytes() < 1 + 8 + MAGIC.length) {
            return false;
        }
        int id = buf.getUnsignedByte(buf.readerIndex());
        if (id != ID_UNCONNECTED_PING && id != ID_UNCONNECTED_PING_OPEN) {
            return false;
        }
        return magicMatches(buf, 1 + 8);
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

    /**
     * BDS / wiki.vg server-id string (trailing IPv4/IPv6 ports required for modern clients).
     * {@code MCPE;motd;proto;ver;online;max;guid;sub;Survival;1;port;port;}
     */
    public static String buildMotd(String motd,
                                   int protocol,
                                   String version,
                                   int online,
                                   int maxPlayers,
                                   long guid,
                                   String subMotd,
                                   int ipv4Port,
                                   int ipv6Port) {
        String safeMotd = sanitizeMotdPart(motd, "YaP Link");
        String safeSub = sanitizeMotdPart(subMotd, "YaP Link");
        String safeVer = version == null || version.isBlank() ? "26.45" : version.replace(';', ' ');
        return "MCPE;" + safeMotd + ";"
                + protocol + ";"
                + safeVer + ";"
                + Math.max(0, online) + ";"
                + Math.max(1, maxPlayers) + ";"
                + Long.toUnsignedString(guid) + ";"
                + safeSub + ";"
                + "Survival;1;"
                + ipv4Port + ";"
                + ipv6Port + ";";
    }

    /** Strip ; and Java/Bedrock color codes so Unconnected Pong MOTD stays parseable. */
    public static String sanitizeMotdPart(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String s = raw.replace(';', ' ');
        s = s.replaceAll("(?i)&[0-9a-fk-or]", "");
        s = s.replaceAll("(?i)§[0-9a-fk-or]", "");
        s = s.replace("§", "");
        s = s.trim();
        return s.isEmpty() ? fallback : s;
    }
}

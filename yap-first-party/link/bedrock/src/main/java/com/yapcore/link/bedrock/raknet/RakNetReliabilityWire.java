package com.yapcore.link.bedrock.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * RakNet address / triad / ACK wire helpers (split from {@link RakNetReliability}).
 */
final class RakNetReliabilityWire {

    private RakNetReliabilityWire() {}

    static ByteBuf buildAck(int... datagramNumbers) {
        ByteBuf out = Unpooled.buffer(32);
        out.writeByte(RakNetReliability.ID_ACK);
        out.writeShort(datagramNumbers.length);
        for (int n : datagramNumbers) {
            out.writeBoolean(true); // single
            writeTriadLe(out, n);
        }
        return out;
    }

    static void writeAddress(ByteBuf out, RakNetReliability.InetAddrCookie addr) {
        out.writeByte(addr.version());
        if (addr.version() == 4) {
            for (int i = 0; i < 4; i++) {
                out.writeByte(~addr.address()[i]);
            }
            out.writeShort(addr.port());
        } else {
            out.writeBytes(addr.address(), 0, Math.min(16, addr.address().length));
            out.writeShort(addr.port());
        }
    }

    static RakNetReliability.InetAddrCookie readAddress(ByteBuf in) {
        byte ver = in.readByte();
        if (ver == 4) {
            byte[] a = new byte[4];
            for (int i = 0; i < 4; i++) {
                a[i] = (byte) (~in.readByte());
            }
            int port = in.readUnsignedShort();
            return new RakNetReliability.InetAddrCookie(ver, a, port);
        }
        byte[] a = new byte[16];
        in.readBytes(a);
        int port = in.readUnsignedShort();
        return new RakNetReliability.InetAddrCookie(ver, a, port);
    }

    static void writeTriadLe(ByteBuf out, int value) {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
        out.writeByte((value >> 16) & 0xFF);
    }

    static int readTriadLe(ByteBuf in) {
        int b0 = in.readUnsignedByte();
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16);
    }
}

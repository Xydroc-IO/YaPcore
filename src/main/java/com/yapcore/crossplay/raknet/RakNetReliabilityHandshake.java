package com.yapcore.crossplay.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Open-connection / CRA / pong / address helpers for {@link RakNetReliability}
 * (split for the ≤500-line domain gate). Nested types stay on {@link RakNetReliability}.
 */
final class RakNetReliabilityHandshake {

    private RakNetReliabilityHandshake() {
    }

    static ByteBuf openConnectionReply1(long serverGuid, boolean security, int mtu) {
        ByteBuf out = Unpooled.buffer(1 + RakNetUnconnected.MAGIC.length + 8 + 1 + 2);
        out.writeByte(RakNetReliability.ID_OPEN_CONNECTION_REPLY_1);
        out.writeBytes(RakNetUnconnected.MAGIC);
        out.writeLong(serverGuid);
        out.writeBoolean(security);
        out.writeShort(mtu);
        return out;
    }

    static ByteBuf openConnectionReply2(long serverGuid, RakNetReliability.InetAddrCookie addr,
                                        int mtu, boolean security) {
        ByteBuf out = Unpooled.buffer(64);
        out.writeByte(RakNetReliability.ID_OPEN_CONNECTION_REPLY_2);
        out.writeBytes(RakNetUnconnected.MAGIC);
        out.writeLong(serverGuid);
        writeAddress(out, addr);
        out.writeShort(mtu);
        out.writeBoolean(security);
        return out;
    }

    static ByteBuf connectionRequestAccepted(RakNetReliability.InetAddrCookie internal,
                                             short systemIndex,
                                             RakNetReliability.InetAddrCookie[] systemAddresses,
                                             long requestTime, long time) {
        ByteBuf out = Unpooled.buffer(512);
        out.writeByte(RakNetReliability.ID_CONNECTION_REQUEST_ACCEPTED);
        writeAddress(out, internal);
        out.writeShort(systemIndex);
        for (int i = 0; i < RakNetReliability.SYSTEM_ADDRESS_COUNT; i++) {
            writeAddress(out, i < systemAddresses.length && systemAddresses[i] != null
                    ? systemAddresses[i] : RakNetReliability.InetAddrCookie.loopback());
        }
        out.writeLong(requestTime);
        out.writeLong(time);
        return out;
    }

    static ByteBuf connectedPong(long pingTime, long pongTime) {
        ByteBuf out = Unpooled.buffer(17);
        out.writeByte(RakNetReliability.ID_CONNECTED_PONG);
        out.writeLong(pingTime);
        out.writeLong(pongTime);
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
}

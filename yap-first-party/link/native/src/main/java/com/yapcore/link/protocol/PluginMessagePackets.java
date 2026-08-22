package com.yapcore.link.protocol;

import io.netty.buffer.ByteBuf;

import java.util.Optional;

/** Play-phase custom payload / plugin message parse helpers (best-effort by protocol). */
public final class PluginMessagePackets {

    public record Parsed(String channel, byte[] data) {
    }

    private PluginMessagePackets() {
    }

    public static Optional<Parsed> tryParseServerbound(int protocol, ByteBuf packet) {
        return tryParse(packet, serverboundPlayId(protocol));
    }

    public static Optional<Parsed> tryParseClientbound(int protocol, ByteBuf packet) {
        return tryParse(packet, clientboundPlayId(protocol));
    }

    private static Optional<Parsed> tryParse(ByteBuf packet, int expectedId) {
        if (packet == null || !packet.isReadable()) {
            return Optional.empty();
        }
        packet.markReaderIndex();
        try {
            int packetId = McCodec.readVarInt(packet);
            if (packetId != expectedId) {
                return Optional.empty();
            }
            String channel = McCodec.readString(packet, 32767);
            if (channel.isBlank()) {
                return Optional.empty();
            }
            byte[] data = new byte[packet.readableBytes()];
            if (data.length > 0) {
                packet.readBytes(data);
            }
            return Optional.of(new Parsed(channel, data));
        } catch (Exception e) {
            return Optional.empty();
        } finally {
            packet.resetReaderIndex();
        }
    }

    public static int serverboundPlayId(int protocol) {
        if (protocol >= 768) {
            return 0x12;
        }
        if (protocol >= 766) {
            return 0x11;
        }
        if (protocol >= 763) {
            return 0x10;
        }
        return 0x0F;
    }

    public static int clientboundPlayId(int protocol) {
        if (protocol >= 768) {
            return 0x6E;
        }
        if (protocol >= 766) {
            return 0x6B;
        }
        if (protocol >= 763) {
            return 0x62;
        }
        return 0x5A;
    }

    public static void writeServerbound(ByteBuf out, int protocol, String channel, byte[] data) {
        McCodec.writeVarInt(out, serverboundPlayId(protocol));
        McCodec.writeString(out, channel);
        if (data != null && data.length > 0) {
            out.writeBytes(data);
        }
    }

    public static void writeClientbound(ByteBuf out, int protocol, String channel, byte[] data) {
        McCodec.writeVarInt(out, clientboundPlayId(protocol));
        McCodec.writeString(out, channel);
        if (data != null && data.length > 0) {
            out.writeBytes(data);
        }
    }
}

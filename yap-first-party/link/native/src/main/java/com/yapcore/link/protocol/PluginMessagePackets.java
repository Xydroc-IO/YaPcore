package com.yapcore.link.protocol;

import io.netty.buffer.ByteBuf;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
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

    /**
     * Try expected play id plus nearby custom_payload ids only (never probe 0x01 — that is add_entity).
     */
    public static Optional<Parsed> tryParseClientboundLoose(int protocol, ByteBuf packet) {
        int primary = clientboundPlayId(protocol);
        int[] candidates = {primary, 0x18, 0x19, 0x17, 0x1A, 0x6E, 0x6B};
        for (int id : candidates) {
            Optional<Parsed> parsed = tryParse(packet, id);
            if (parsed.isPresent()) {
                String ch = parsed.get().channel();
                // Reject garbage from wrong-id collisions (channels look like namespace:key).
                if (ch.indexOf(':') >= 0 || ch.equalsIgnoreCase("BungeeCord")) {
                    return parsed;
                }
            }
        }
        return Optional.empty();
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

    /** True when the leading VarInt is a plausible play clientbound custom_payload id. */
    public static boolean looksLikeClientboundCustomPayload(int protocol, ByteBuf packet) {
        if (packet == null || !packet.isReadable()) {
            return false;
        }
        packet.markReaderIndex();
        try {
            int packetId = McCodec.readVarInt(packet);
            int primary = clientboundPlayId(protocol);
            return packetId == primary
                    || packetId == 0x18
                    || packetId == 0x19
                    || packetId == 0x17
                    || packetId == 0x1A
                    || packetId == 0x6E
                    || packetId == 0x6B;
        } catch (Exception e) {
            return false;
        } finally {
            packet.resetReaderIndex();
        }
    }

    /**
     * Find a DataOutputStream {@code writeUTF("Connect") + writeUTF(target)} payload anywhere
     * in the packet — survives wrong custom_payload packet ids.
     */
    public static Optional<String> sniffBungeeConnectTarget(ByteBuf packet) {
        if (packet == null || !packet.isReadable()) {
            return Optional.empty();
        }
        byte[] needle = encodeJavaUtf("Connect");
        byte[] hay = new byte[packet.readableBytes()];
        packet.getBytes(packet.readerIndex(), hay);
        int idx = indexOf(hay, needle);
        if (idx < 0) {
            return Optional.empty();
        }
        int after = idx + needle.length;
        if (after + 2 > hay.length) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(hay, after, hay.length - after))) {
            String target = in.readUTF();
            if (target == null || target.isBlank()) {
                return Optional.empty();
            }
            // Basic server-name shape (lobby / survival / hub-2).
            for (int i = 0; i < target.length(); i++) {
                char c = target.charAt(i);
                if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) {
                    return Optional.empty();
                }
            }
            return Optional.of(target.trim());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static byte[] encodeJavaUtf(String s) {
        byte[] chars = s.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[2 + chars.length];
        out[0] = (byte) ((chars.length >>> 8) & 0xFF);
        out[1] = (byte) (chars.length & 0xFF);
        System.arraycopy(chars, 0, out, 2, chars.length);
        return out;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public static int serverboundPlayId(int protocol) {
        // 1.21.5+ / YaP-Folia 26.x (protocol 773+): play custom_payload = 22
        if (protocol >= 773) {
            return 0x16;
        }
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
        // 1.21.5+ / YaP-Folia 26.x (protocol 773+): play custom_payload = 24
        if (protocol >= 773) {
            return 0x18;
        }
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

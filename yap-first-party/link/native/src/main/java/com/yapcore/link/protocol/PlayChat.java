package com.yapcore.link.protocol;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Play-phase clientbound packets (system chat, disconnect). */
public final class PlayChat {

    private PlayChat() {
    }

    public static ByteBuf systemChatPacket(int protocol, String jsonComponent, boolean overlay) {
        ByteBuf body = Unpooled.buffer();
        int packetId = systemChatId(protocol);
        McCodec.writeVarInt(body, packetId);
        McCodec.writeString(body, jsonComponent);
        if (protocol >= 759) {
            body.writeBoolean(overlay);
        }
        return body;
    }

    public static ByteBuf disconnectPacket(int protocol, String jsonReason) {
        ByteBuf body = Unpooled.buffer();
        McCodec.writeVarInt(body, disconnectId(protocol));
        McCodec.writeString(body, jsonReason);
        return body;
    }

    public static String jsonText(String plain) {
        JsonObject o = new JsonObject();
        o.addProperty("text", plain);
        return o.toString();
    }

    /** Play clientbound {@code minecraft:login} (from protocol dumps). */
    public static int playLoginId(int protocol) {
        if (protocol >= 775) {
            return 49;
        }
        if (protocol >= 773) {
            return 48;
        }
        if (protocol >= 768) {
            return 44;
        }
        if (protocol >= 766) {
            return 43;
        }
        if (protocol >= 764) {
            return 41;
        }
        return 0x28;
    }

    /**
     * Play clientbound {@code minecraft:system_chat}. 26.2 is 121 — the old 0x73
     * id is {@code set_titles_animation} on that protocol.
     */
    public static int systemChatId(int protocol) {
        if (protocol >= 775) {
            return 121;
        }
        if (protocol >= 773) {
            return 119;
        }
        if (protocol >= 771) {
            return 114;
        }
        if (protocol >= 768) {
            return 115;
        }
        if (protocol >= 766) {
            return 108;
        }
        if (protocol >= 764) {
            return 103;
        }
        return 0x5F;
    }

    /** Flip play-login {@code enforcesSecureChat} so the client skips the unverified toast. */
    public static void advertiseSecureChat(int protocol, io.netty.buffer.ByteBuf packet) {
        int reader = packet.readerIndex();
        try {
            int id = McCodec.readVarInt(packet);
            if (id != playLoginId(protocol) || packet.writerIndex() <= reader) {
                return;
            }
            int last = packet.writerIndex() - 1;
            if (packet.getByte(last) == 0) {
                packet.setByte(last, 1);
            }
        } catch (Exception ignored) {
            // leave packet unchanged
        } finally {
            packet.readerIndex(reader);
        }
    }

    private static int disconnectId(int protocol) {
        if (protocol >= 768) {
            return 0x1D;
        }
        if (protocol >= 766) {
            return 0x1D;
        }
        if (protocol >= 763) {
            return 0x1A;
        }
        return 0x1A;
    }
}

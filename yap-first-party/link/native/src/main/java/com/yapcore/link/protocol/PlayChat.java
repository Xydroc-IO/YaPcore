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

    private static int systemChatId(int protocol) {
        if (protocol >= 768) {
            return 0x73;
        }
        if (protocol >= 766) {
            return 0x70;
        }
        if (protocol >= 763) {
            return 0x67;
        }
        return 0x5F;
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

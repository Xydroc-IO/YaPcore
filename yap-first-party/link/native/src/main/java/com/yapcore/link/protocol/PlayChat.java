package com.yapcore.link.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/** Play-phase clientbound packets (system chat, disconnect). */
public final class PlayChat {

    private PlayChat() {
    }

    public static ByteBuf systemChatPacket(int protocol, String jsonComponent, boolean overlay) {
        ByteBuf body = Unpooled.buffer();
        int packetId = systemChatId(protocol);
        McCodec.writeVarInt(body, packetId);
        writeTextComponent(body, protocol, jsonComponent);
        if (protocol >= 759) {
            body.writeBoolean(overlay);
        }
        return body;
    }

    public static ByteBuf disconnectPacket(int protocol, String jsonReason) {
        ByteBuf body = Unpooled.buffer();
        McCodec.writeVarInt(body, disconnectId(protocol));
        writeTextComponent(body, protocol, jsonReason);
        return body;
    }

    public static String jsonText(String plain) {
        JsonObject o = new JsonObject();
        o.addProperty("text", plain == null ? "" : plain);
        return o.toString();
    }

    /**
     * 1.20.3+ (protocol ≥ 765): Text Component is network NBT (root TAG_String for plain text).
     * Older: JSON string.
     */
    static void writeTextComponent(ByteBuf buf, int protocol, String jsonOrPlain) {
        if (protocol >= 765) {
            writeNbtStringComponent(buf, plainText(jsonOrPlain));
        } else {
            McCodec.writeString(buf, jsonOrPlain == null ? "{\"text\":\"\"}" : jsonOrPlain);
        }
    }

    /** Root TAG_String — valid for unstyled text components. */
    static void writeNbtStringComponent(ByteBuf buf, String plain) {
        buf.writeByte(8); // TAG_String
        writeModifiedUtf(buf, plain == null ? "" : plain);
    }

    /** Java {@link DataOutputStream#writeUTF} (unsigned short length + modified UTF-8). */
    static void writeModifiedUtf(ByteBuf buf, String s) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(s.length() + 16);
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeUTF(s);
            byte[] encoded = bos.toByteArray();
            buf.writeBytes(encoded);
        } catch (Exception e) {
            // Extremely long / bad input — fall back to empty string tag payload.
            buf.writeShort(0);
        }
    }

    static String plainText(String jsonOrPlain) {
        if (jsonOrPlain == null || jsonOrPlain.isBlank()) {
            return "";
        }
        String t = jsonOrPlain.trim();
        if (t.startsWith("{") && t.contains("\"text\"")) {
            try {
                var el = JsonParser.parseString(t);
                if (el.isJsonObject() && el.getAsJsonObject().has("text")) {
                    return el.getAsJsonObject().get("text").getAsString();
                }
            } catch (Exception ignored) {
                // use raw
            }
        }
        return jsonOrPlain;
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

    /** Play disconnect is {@code 0x20} on 26.2; older builds used {@code 0x1D}. */
    private static int disconnectId(int protocol) {
        if (protocol >= 775) {
            return 0x20;
        }
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

package com.yapcore.crossplay.bedrock.codec;

import com.yapcore.crossplay.bedrock.BedrockAvailableCommands;
import com.yapcore.crossplay.bedrock.BedrockItemStates;
import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import com.yapcore.crossplay.bedrock.BedrockPacketIds;
import com.yapcore.crossplay.bedrock.BedrockPaperRecipes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import static com.yapcore.crossplay.bedrock.codec.BedrockCodecBinary.*;

public final class BedrockTextCodec {
    private BedrockTextCodec() {}
    public static ByteBuf textChat(String source, String message) {
        ByteBuf out = Unpooled.buffer(64 + message.length());
        writeUnsignedVarInt(out, BedrockPacketCodec.ID_TEXT);
        out.writeByte(1); // chat
        out.writeBoolean(false); // needs translation
        writeString(out, source);
        writeString(out, message);
        writeString(out, ""); // xuid
        writeString(out, ""); // platform
        writeString(out, ""); // filtered_message (1.21.50+)
        return out;
    }
    public static BedrockPacketCodec.TextDecode tryDecodeText(ByteBuf body) {
        try {
            int type = body.readUnsignedByte();
            boolean needsTranslation = body.readBoolean();
            String source = "";
            if (type == 1 || type == 3) {
                source = readString(body);
            }
            String message = readString(body);
            skipTextTrailingFields(body);
            return new BedrockPacketCodec.TextDecode(type, needsTranslation, source, message);
        } catch (Exception e) {
            return null;
        }
    }

    private static void skipTextTrailingFields(ByteBuf body) {
        if (!body.isReadable()) {
            return;
        }
        try {
            readString(body); // xuid
            readString(body); // platform_chat_id
            if (body.isReadable()) {
                readString(body); // filtered_message (1.21.50+)
            }
        } catch (Exception ignored) {
        }
    }

    public record TextDecode(int type, boolean needsTranslation, String source, String message) {
    }

    /** Decode {@code packet_command_request} — command string is first field. */
    public static BedrockPacketCodec.CommandRequestDecode tryDecodeCommandRequest(ByteBuf body) {
        try {
            String command = readString(body);
            return new BedrockPacketCodec.CommandRequestDecode(command == null ? "" : command);
        } catch (Exception e) {
            return null;
        }
    }

    public record CommandRequestDecode(String command) {
    }

    /**
     * Minimal {@code command_output} success/failure toast for the requesting player.
     * Schema: origin (player) + success bool + message count + messages.
     */
    public static ByteBuf commandOutputSimple(String message, boolean success) {
        ByteBuf out = Unpooled.buffer(64 + (message == null ? 0 : message.length()));
        writeUnsignedVarInt(out, BedrockPacketIds.COMMAND_OUTPUT.id);
        // CommandOrigin: player
        writeUnsignedVarInt(out, 0);
        out.writeLongLE(0L);
        out.writeLongLE(0L);
        writeString(out, ""); // request_id
        // player_entity_id switch void for type=player
        out.writeByte(3); // output_type = all
        writeUnsignedVarInt(out, success ? 1 : 0); // success_count
        writeUnsignedVarInt(out, 1); // output messages
        out.writeBoolean(success);
        writeString(out, message == null ? "" : message);
        writeUnsignedVarInt(out, 0); // params
        return out;
    }
}

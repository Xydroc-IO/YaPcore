package com.yapcore.presence;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Bidirectional body for Bukkit channel {@code yap:presence}.
 * Raw UTF-8 (no Minecraft string framing) so Paper's plugin messenger
 * delivers the same bytes {@code PresenceChannel} parses.
 *
 * <pre>
 *   HELLO / UI|SYNC / WARDROBE|* / SKIN|URL|* / SKIN|MODEL|* / SKIN|CAPE|* / EMOTE|&lt;id&gt;
 *   SKIN|&lt;uuid&gt;|… · EMOTE|&lt;playerUuid&gt;|&lt;id&gt; · MOVEMENT|… · WARDROBE|v1|… · EMOTE_CATALOG|v1|…
 * </pre>
 */
public record PresenceChannelPayload(String message) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("yap", "presence");
    public static final Type<PresenceChannelPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, PresenceChannelPayload> STREAM_CODEC =
            CustomPacketPayload.codec(PresenceChannelPayload::write, PresenceChannelPayload::new);

    public static PresenceChannelPayload hello() {
        return new PresenceChannelPayload("HELLO");
    }

    public static PresenceChannelPayload emote(String bedrockEmoteId) {
        return new PresenceChannelPayload("EMOTE|" + bedrockEmoteId);
    }

    public PresenceChannelPayload {
        if (message == null) {
            throw new IllegalArgumentException("message");
        }
    }

    private PresenceChannelPayload(FriendlyByteBuf buf) {
        this(readUtfRaw(buf));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBytes(message.getBytes(StandardCharsets.UTF_8));
    }

    private static String readUtfRaw(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

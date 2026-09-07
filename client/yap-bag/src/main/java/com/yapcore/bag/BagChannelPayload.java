package com.yapcore.bag;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Serverbound body for Bukkit channel {@code yap:bag}.
 * Raw UTF-8 (no Minecraft string framing) so Paper's plugin messenger
 * delivers the same bytes {@link com.yapcore.playerdata.bag.BackpackChannel} parses.
 *
 * <pre>
 *   HELLO           — client has page tabs; server omits chest-item nav
 *   OPEN            — open page 1
 *   OPEN|&lt;page&gt;     — open that page
 * </pre>
 */
public record BagChannelPayload(String message) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("yap", "bag");
    public static final Type<BagChannelPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, BagChannelPayload> STREAM_CODEC =
            CustomPacketPayload.codec(BagChannelPayload::write, BagChannelPayload::new);

    public static BagChannelPayload hello() {
        return new BagChannelPayload("HELLO");
    }

    public static BagChannelPayload open(int page) {
        if (page <= 1) {
            return new BagChannelPayload("OPEN");
        }
        return new BagChannelPayload("OPEN|" + page);
    }

    private BagChannelPayload(FriendlyByteBuf buf) {
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

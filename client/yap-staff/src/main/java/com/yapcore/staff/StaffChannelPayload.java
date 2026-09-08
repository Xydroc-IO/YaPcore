package com.yapcore.staff;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Serverbound body for Bukkit channel {@code yap:staff}.
 * Raw UTF-8 (no Minecraft string framing) so Paper's plugin messenger
 * delivers the same bytes YaPAdmin {@code StaffChannel} parses.
 *
 * <pre>
 *   RUN|&lt;command without leading slash&gt;
 * </pre>
 */
public record StaffChannelPayload(String message) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("yap", "staff");
    public static final Type<StaffChannelPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, StaffChannelPayload> STREAM_CODEC =
            CustomPacketPayload.codec(StaffChannelPayload::write, StaffChannelPayload::new);

    public static StaffChannelPayload run(String commandWithoutSlash) {
        return new StaffChannelPayload("RUN|" + commandWithoutSlash);
    }

    private StaffChannelPayload(FriendlyByteBuf buf) {
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

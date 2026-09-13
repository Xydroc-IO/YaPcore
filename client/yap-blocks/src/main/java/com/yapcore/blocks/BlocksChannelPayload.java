package com.yapcore.blocks;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/** Bukkit channel {@code yap:blocks} — HELLO for parity.bedrock-feel gate. */
public record BlocksChannelPayload(String message) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("yap", "blocks");
    public static final Type<BlocksChannelPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, BlocksChannelPayload> STREAM_CODEC =
            CustomPacketPayload.codec(BlocksChannelPayload::write, BlocksChannelPayload::new);

    public static BlocksChannelPayload hello() {
        return new BlocksChannelPayload("HELLO");
    }

    private BlocksChannelPayload(FriendlyByteBuf buf) {
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

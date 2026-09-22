package com.yapcore.haze;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Clientbound/serverbound body for Bukkit channel {@code yap:420}.
 * Raw UTF-8 matching server {@code HazePayload}.
 */
public record HazeChannelPayload(String message) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("yap", "420");
    public static final Type<HazeChannelPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, HazeChannelPayload> STREAM_CODEC =
            CustomPacketPayload.codec(HazeChannelPayload::write, HazeChannelPayload::new);

    public static HazeChannelPayload hello() {
        return new HazeChannelPayload("HELLO");
    }

    private HazeChannelPayload(FriendlyByteBuf buf) {
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

    public static boolean send(HazeChannelPayload payload) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null) {
            return false;
        }
        try {
            minecraft.getConnection().send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(payload));
            return true;
        } catch (Exception e) {
            Yap420Client.LOGGER.warn("Failed to send yap:420 {}", payload.message(), e);
            return false;
        }
    }

    public static void handleInbound(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (text.regionMatches(true, 0, "HAZE|", 0, 5)) {
            String[] parts = text.split("\\|");
            if (parts.length < 3) {
                return;
            }
            try {
                double intensity = Double.parseDouble(parts[1]);
                int duration = Integer.parseInt(parts[2]);
                Yap420Client.applyHaze(intensity, duration);
            } catch (NumberFormatException ignored) {
            }
        }
    }
}

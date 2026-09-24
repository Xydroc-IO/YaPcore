package com.yapcore.chat;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure helpers for cross-server chat relay: speak-channel resolution, payload encode/decode,
 * and relay eligibility. Keeps {@link com.yapcore.chat.service.ChatServiceImpl} thin and testable.
 */
public final class ChatRelayOps {

    public static final String PAYLOAD_PREFIX = "RELAY|";

    public record Speak(String channel, String message) {
    }

    public record RelayPacket(String channelId, String serverId, UUID senderUuid,
                              String senderName, String message) {
    }

    private ChatRelayOps() {
    }

    /** Resolve channel + message text from stored channel and typed line ({@code !} → local). */
    public static Speak resolveSpeak(ChatConfig config, String storedChannel, String plainMessage) {
        String plain = plainMessage == null ? "" : plainMessage;
        String channel = storedChannel == null || storedChannel.isBlank()
                ? config.defaultChannel()
                : storedChannel.toLowerCase(Locale.ROOT);
        String message = plain;
        String prefix = config.localPrefix();
        if (prefix != null && !prefix.isEmpty()
                && plain.startsWith(prefix) && plain.length() > prefix.length()) {
            channel = "local";
            message = plain.substring(prefix.length()).trim();
        }
        if (!config.channels().containsKey(channel) && !"local".equals(channel)) {
            channel = config.defaultChannel();
        }
        return new Speak(channel, message);
    }

    public static boolean shouldRelay(ChatConfig config, String channelId) {
        if (config == null || !config.networkEnabled() || channelId == null) {
            return false;
        }
        return config.networkRelayChannels().contains(channelId.toLowerCase(Locale.ROOT));
    }

    public static String encode(String channelId, String serverId, UUID senderUuid,
                                String senderName, String plainMessage) {
        String safeMessage = plainMessage == null ? "" : plainMessage.replace('|', '/');
        String name = senderName == null ? "" : senderName.replace('|', '/');
        return PAYLOAD_PREFIX + channelId + "|" + serverId + "|"
                + senderUuid + "|" + name + "|" + safeMessage;
    }

    public static byte[] encodeBytes(String channelId, String serverId, UUID senderUuid,
                                     String senderName, String plainMessage) {
        return encode(channelId, serverId, senderUuid, senderName, plainMessage)
                .getBytes(StandardCharsets.UTF_8);
    }

    public static Optional<RelayPacket> parse(byte[] data) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }
        return parse(new String(data, StandardCharsets.UTF_8));
    }

    public static Optional<RelayPacket> parse(String payload) {
        if (payload == null || !payload.startsWith(PAYLOAD_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = payload.split("\\|", 6);
        if (parts.length < 6) {
            return Optional.empty();
        }
        UUID senderUuid;
        try {
            senderUuid = UUID.fromString(parts[3]);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.of(new RelayPacket(parts[1], parts[2], senderUuid, parts[4], parts[5]));
    }
}

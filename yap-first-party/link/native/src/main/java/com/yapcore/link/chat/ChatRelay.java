package com.yapcore.link.chat;

import com.yapcore.link.LinkServer;
import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.session.PlayerHub;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Cross-server chat relay (Phase 2). Parses serverbound chat from bridged clients and
 * fans out to other players on YaP Link. Mirrors {@code ChatService#relayNetworkMessage} semantics.
 */
public final class ChatRelay {

    private static final Logger LOG = Logger.getLogger("YaP.Link.ChatRelay");

    private final LinkServer server;

    public ChatRelay(LinkServer server) {
        this.server = server;
    }

    public boolean enabled() {
        return server.config().chatRelayEnabled();
    }

    /**
     * Inspect a serverbound play packet; relay if it looks like chat.
     *
     * @return true if packet was consumed (caller should not forward)
     */
    public boolean tryRelayClientPacket(
            int protocol,
            UUID senderId,
            String senderName,
            String backendName,
            io.netty.buffer.ByteBuf packet
    ) {
        if (!server.config().chatRelayEnabled()) {
            return false;
        }
        packet.markReaderIndex();
        try {
            if (packet.readableBytes() < 2) {
                return false;
            }
            int packetId = McCodec.readVarInt(packet);
            String message = extractChatMessage(protocol, packetId, packet);
            if (message == null || message.isBlank() || message.startsWith("/")) {
                return false;
            }
            relay(senderId, senderName, backendName, message);
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            packet.resetReaderIndex();
        }
    }

    public void relay(UUID senderId, String senderName, String backendName, String plainMessage) {
        if (!server.config().chatRelayEnabled()) {
            return;
        }
        String formatted = server.config().chatRelayFormat()
                .replace("{server}", backendName)
                .replace("{name}", senderName)
                .replace("{message}", plainMessage)
                .replace("{channel}", server.config().chatRelayChannel());
        server.playerHub().broadcastPlain(formatted, senderId);
        LOG.info("CHAT relay [" + backendName + "] " + senderName + ": " + plainMessage);
    }

    public void relayNetworkMessage(String channelId, String serverId,
                                    UUID senderUuid, String senderName, String plainMessage) {
        if (!server.config().chatRelayEnabled()) {
            return;
        }
        if (channelId != null && !channelId.isBlank()
                && !channelId.equalsIgnoreCase(server.config().chatRelayChannel())) {
            return;
        }
        relay(senderUuid, senderName, serverId, plainMessage);
    }

    public void announceJoin(String username, String backend) {
        if (!server.config().chatRelayEnabled() || !server.config().chatJoinAnnounce()) {
            return;
        }
        server.playerHub().broadcastPlain(
                "[" + server.config().chatRelayChannel() + "] " + username + " joined " + backend,
                null);
    }

    public void announceLeave(String username, String backend) {
        if (!server.config().chatRelayEnabled() || !server.config().chatJoinAnnounce()) {
            return;
        }
        server.playerHub().broadcastPlain(
                "[" + server.config().chatRelayChannel() + "] " + username + " left " + backend,
                null);
    }

    private static String extractChatMessage(int protocol, int packetId, io.netty.buffer.ByteBuf buf) {
        boolean chatLike;
        if (protocol >= 768) {
            chatLike = packetId == 0x05 || packetId == 0x04;
        } else if (protocol >= 766) {
            chatLike = packetId == 0x05 || packetId == 0x04;
        } else if (protocol >= 763) {
            chatLike = packetId == 0x04 || packetId == 0x03;
        } else {
            chatLike = packetId == 0x03 || packetId == 0x02;
        }
        if (!chatLike) {
            return null;
        }
        try {
            if (protocol >= 766 && packetId >= 0x04) {
                return scanFirstUtf8String(buf);
            }
            return McCodec.readString(buf, 256);
        } catch (Exception e) {
            return scanFirstUtf8String(buf);
        }
    }

    private static String scanFirstUtf8String(io.netty.buffer.ByteBuf buf) {
        int start = buf.readerIndex();
        int end = buf.writerIndex();
        for (int i = start; i < end - 2; i++) {
            int len = buf.getUnsignedByte(i);
            if (len >= 1 && len <= 200 && i + 1 + len <= end) {
                byte[] bytes = new byte[len];
                buf.getBytes(i + 1, bytes);
                String s = new String(bytes, StandardCharsets.UTF_8);
                if (looksLikeChat(s)) {
                    return s;
                }
            }
        }
        return null;
    }

    private static boolean looksLikeChat(String s) {
        if (s.isBlank() || s.length() > 256) {
            return false;
        }
        return !s.contains("\0");
    }
}

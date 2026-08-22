package com.yapcore.link;

import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.event.PluginMessageEvent;
import com.yapcore.link.api.event.ServerChooseEvent;
import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.protocol.PlayChat;
import com.yapcore.link.protocol.PluginMessagePackets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

/** Play-phase routing: plugin messages and /server transfers. */
final class ClientSessionRouting {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Client");

    private ClientSessionRouting() {
    }

    static void tryFirePluginMessage(ClientSession session, ByteBuf buf, boolean fromClient) {
        if (!session.server.config().pluginsEnabled()) {
            return;
        }
        if (session.server.plugins().registeredChannelIds().isEmpty()) {
            return;
        }
        Optional<PluginMessagePackets.Parsed> parsed = fromClient
                ? PluginMessagePackets.tryParseServerbound(session.protocolVersion, buf)
                : PluginMessagePackets.tryParseClientbound(session.protocolVersion, buf);
        if (parsed.isEmpty()) {
            return;
        }
        String channelId = parsed.get().channel();
        if (!session.server.plugins().isRegisteredChannel(channelId)) {
            return;
        }
        ChannelIdentifier channel = ChannelIdentifier.fromMcChannel(channelId);
        PluginMessageEvent event = new PluginMessageEvent(
                fromClient ? PluginMessageEvent.SourceKind.PLAYER : PluginMessageEvent.SourceKind.BACKEND,
                fromClient ? Optional.ofNullable(session.playerHandle) : Optional.empty(),
                fromClient ? Optional.empty() : session.currentServer(),
                channel,
                parsed.get().data()
        );
        session.server.plugins().eventBus().fire(event);
        session.server.metrics().counter("plugin.messages", 1);
    }

    static String extractServerCommand(ByteBuf buf) {
        buf.markReaderIndex();
        try {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            String s = new String(bytes, StandardCharsets.UTF_8);
            int idx = indexOfIgnoreCase(s, "server ");
            if (idx < 0) {
                return null;
            }
            if (idx > 0) {
                char c = s.charAt(idx - 1);
                if (Character.isLetterOrDigit(c)) {
                    return null;
                }
            }
            String rest = s.substring(idx + "server ".length()).trim();
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (Character.isWhitespace(c) || c == '\0' || c == '"') {
                    break;
                }
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                    name.append(c);
                } else {
                    break;
                }
            }
            return name.length() == 0 ? null : name.toString();
        } finally {
            buf.resetReaderIndex();
        }
    }

    private static int indexOfIgnoreCase(String hay, String needle) {
        return hay.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    static void handleServerCommand(ClientSession session, String serverName) {
        LinkConfig.Backend target = session.server.config().findServer(serverName);
        Channel client = session.clientCtx != null ? session.clientCtx.channel() : null;
        if (target == null) {
            session.sendPlaySystemChat(client, PlayChat.jsonText("Unknown server: " + serverName
                    + " — known: " + String.join(", ", session.server.config().servers().keySet())));
            return;
        }
        if (!session.server.backendMonitor().isUp(target.name())) {
            session.sendPlaySystemChat(client, PlayChat.jsonText("Server " + target.name() + " is currently unavailable."));
            return;
        }
        if (target.name().equalsIgnoreCase(session.currentBackendName)) {
            session.sendPlaySystemChat(client, PlayChat.jsonText("Already connected to " + target.name()));
            return;
        }
        RegisteredServer reg = session.server.plugins().proxy().server(target.name()).orElse(null);
        if (reg != null && session.playerHandle != null) {
            ServerChooseEvent choose = new ServerChooseEvent(session.playerHandle, reg);
            session.server.plugins().eventBus().fire(choose);
            if (choose.isCancelled()) {
                return;
            }
            if (choose.target() != null) {
                LinkConfig.Backend redirected = session.server.config().findServer(choose.target().name());
                if (redirected != null) {
                    target = redirected;
                }
            }
        }
        LOG.info("SERVER user=" + session.username + " → " + target.name());
        session.server.redirects().put(session.playerId, target.name());
        if (session.protocolVersion >= 766 && client != null && client.isActive()) {
            ByteBuf transfer = Unpooled.buffer();
            McCodec.writeVarInt(transfer, transferPacketId(session.protocolVersion));
            McCodec.writeString(transfer, session.server.config().publicHost());
            McCodec.writeVarInt(transfer, session.server.config().publicPort());
            client.writeAndFlush(transfer).addListener(f -> {
                if (session.backend != null) {
                    session.backend.close();
                }
            });
            return;
        }
        if (session.backend != null) {
            session.backend.close();
        }
        session.kickChannel(client, "Sending you to " + target.name() + " — reconnect to YaP Link.");
    }

    private static int transferPacketId(int protocol) {
        return protocol >= 768 ? 0x7A : 0x73;
    }
}

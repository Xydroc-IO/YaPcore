package com.yapcore.link;

import com.yapcore.link.api.ChannelIdentifier;
import com.yapcore.link.api.RegisteredServer;
import com.yapcore.link.api.event.PluginMessageEvent;
import com.yapcore.link.api.event.ServerChooseEvent;
import com.yapcore.link.protocol.McCodec;
import com.yapcore.link.protocol.PlayChat;
import com.yapcore.link.protocol.PluginMessagePackets;
import io.netty.buffer.ByteBuf;
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

    /** @return {@code true} if a plugin set {@link PluginMessageEvent.Result#HANDLED} (do not relay). */
    static boolean tryFirePluginMessage(ClientSession session, ByteBuf buf, boolean fromClient) {
        if (!session.server.config().pluginsEnabled()) {
            return false;
        }
        if (session.server.plugins().registeredChannelIds().isEmpty()) {
            return false;
        }
        Optional<PluginMessagePackets.Parsed> parsed = fromClient
                ? PluginMessagePackets.tryParseServerbound(session.protocolVersion, buf)
                : PluginMessagePackets.tryParseClientbound(session.protocolVersion, buf);
        // Folia/Paper packet-id drift: still catch BungeeCord Connect by payload shape.
        if (parsed.isEmpty() && !fromClient) {
            parsed = PluginMessagePackets.tryParseClientboundLoose(session.protocolVersion, buf);
        }
        if (parsed.isEmpty() && !fromClient) {
            if (PluginMessagePackets.looksLikeClientboundCustomPayload(session.protocolVersion, buf)) {
                Optional<String> connectTarget = PluginMessagePackets.sniffBungeeConnectTarget(buf);
                if (connectTarget.isPresent()) {
                    LOG.info("BungeeCord Connect sniff user=" + session.username
                            + " → " + connectTarget.get()
                            + " (packet-id parse missed; payload matched)");
                    handleServerCommand(session, connectTarget.get());
                    return true;
                }
            }
            return false;
        }
        if (parsed.isEmpty()) {
            return false;
        }
        String channelId = parsed.get().channel();
        if (!session.server.plugins().isRegisteredChannel(channelId) && !isBungeeChannelName(channelId)) {
            return false;
        }
        ChannelIdentifier channel = ChannelIdentifier.fromMcChannel(channelId);
        PluginMessageEvent event = new PluginMessageEvent(
                fromClient ? PluginMessageEvent.SourceKind.PLAYER : PluginMessageEvent.SourceKind.BACKEND,
                Optional.ofNullable(session.playerHandle),
                fromClient ? Optional.empty() : session.currentServer(),
                channel,
                parsed.get().data());
        session.server.plugins().eventBus().fire(event);
        session.server.metrics().counter("plugin.messages", 1);
        if (event.result() == PluginMessageEvent.Result.HANDLED) {
            return true;
        }
        if (!fromClient) {
            Optional<String> connectTarget = PluginMessagePackets.sniffBungeeConnectTarget(buf);
            if (connectTarget.isPresent() && session.playerHandle != null) {
                LOG.info("BungeeCord Connect fallback user=" + session.username
                        + " → " + connectTarget.get());
                handleServerCommand(session, connectTarget.get());
                return true;
            }
        }
        return false;
    }

    private static boolean isBungeeChannelName(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return false;
        }
        String id = channelId.toLowerCase(Locale.ROOT);
        return id.equals("bungeecord:main")
                || id.equals("minecraft:bungeecord")
                || id.equals("bungeecord")
                || id.endsWith(":bungeecord");
    }

    /** Play clientbound {@code login} (aka Join Game) — 26.2 id 49. */
    static boolean isPlayLoginPacket(int protocol, ByteBuf buf) {
        if (buf == null || !buf.isReadable()) {
            return false;
        }
        buf.markReaderIndex();
        try {
            int packetId = McCodec.readVarInt(buf);
            int loginId = protocol >= 773 ? 49 : 43;
            return packetId == loginId;
        } catch (Exception e) {
            return false;
        } finally {
            buf.resetReaderIndex();
        }
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
        LOG.info("SERVER user=" + session.username + " → " + target.name()
                + " proto=" + session.protocolVersion);
        // In-proxy soft swap (Velocity-style): keep client TCP, rebind backend via
        // play start_configuration → config → play. No disconnect screen.
        ClientSessionSoftSwitch.begin(session, target);
    }
}

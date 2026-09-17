package com.yapcore.link;

import com.yapcore.link.api.LinkPlayer;
import com.yapcore.link.api.SimpleCommand;
import com.yapcore.link.protocol.McCodec;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Intercepts JE {@code chat_command} / {@code chat_command_signed} for Link plugin
 * commands ({@code /hub}, {@code /server}, …). Without this, registered commands are
 * never executed and fall through to Folia as unknown server commands.
 */
final class ClientSessionCommands {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Client");

    private ClientSessionCommands() {
    }

    /**
     * @return {@code true} if a registered Link command handled the packet (do not relay)
     */
    static boolean tryDispatchRegistered(ClientSession session, ByteBuf buf) {
        if (!session.server.config().pluginsEnabled()) {
            return false;
        }
        String line = extractCommandLine(session.protocolVersion, buf);
        if (line == null || line.isBlank()) {
            return false;
        }
        String trimmed = line.startsWith("/") ? line.substring(1).trim() : line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        String[] parts = trimmed.split("\\s+");
        String name = parts[0].toLowerCase(Locale.ROOT);
        SimpleCommand cmd = session.server.plugins().command(name);
        if (cmd == null) {
            return false;
        }
        LinkPlayer player = session.playerHandle();
        if (!(player instanceof SimpleCommand.CommandSource source)) {
            return false;
        }
        if (!cmd.hasPermission(source)) {
            player.sendMessage("§cNo permission for /" + name);
            return true;
        }
        String[] args = parts.length > 1
                ? Arrays.copyOfRange(parts, 1, parts.length)
                : new String[0];
        try {
            cmd.execute(source, args);
            LOG.fine("LINK-CMD user=" + session.username + " /" + trimmed);
        } catch (Exception e) {
            LOG.warning("Link command /" + name + " failed: " + e.getMessage());
            player.sendMessage("§cCommand failed: " + e.getMessage());
        }
        return true;
    }

    /**
     * Best-effort parse of unsigned/signed chat_command payload → command line
     * without leading slash (e.g. {@code hub} or {@code server survival}).
     */
    static String extractCommandLine(int protocol, ByteBuf buf) {
        buf.markReaderIndex();
        try {
            int id = McCodec.readVarInt(buf);
            if (!isChatCommandPacket(protocol, id)) {
                return null;
            }
            return McCodec.readString(buf, 256);
        } catch (Exception e) {
            return null;
        } finally {
            buf.resetReaderIndex();
        }
    }

    /** Protocol 26.2 / modern: unsigned=7, signed=8 (see JavaPlayWire.SB_CHAT_COMMAND). */
    static boolean isChatCommandPacket(int protocol, int packetId) {
        if (protocol >= 766) {
            return packetId == 7 || packetId == 8;
        }
        if (protocol >= 763) {
            return packetId == 4 || packetId == 5;
        }
        return packetId == 3 || packetId == 4;
    }
}

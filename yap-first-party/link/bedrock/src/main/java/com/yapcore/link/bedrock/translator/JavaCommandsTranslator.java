package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.cloudburst.LinkJoinPackets;
import com.yapcore.link.bedrock.downstream.JavaCommandsTree;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;

/**
 * JE {@code commands} Brigadier tree → Bedrock {@link AvailableCommandsPacket}.
 *
 * <p>Prefers full tree parse ({@link JavaCommandsTree}) with real overloads. Falls back to
 * vanilla+literal extras when the tree cannot be decoded.
 */
public final class JavaCommandsTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaCommandsTranslator() {
    }

    /** Preferred path: full JE commands tree → AvailableCommands with mapped overloads. */
    public static void onCommandsTree(LinkBedrockSession session, JavaCommandsTree.Parsed tree) {
        if (session == null || !session.isSentSpawnPacket() || tree == null) {
            return;
        }
        List<String> names = tree.rootLiteralNames();
        session.rememberPluginCommands(names);
        AvailableCommandsPacket packet = tree.toAvailableCommands();
        if (packet.getCommands().isEmpty()) {
            onCommands(session, names);
            return;
        }
        session.sendUpstreamPacket(packet);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_commands_tree→be AvailableCommands cmds=" + packet.getCommands().size()
                        + " roots=" + names.size());
        LOG.info("BE AvailableCommands from JE tree user=" + session.username()
                + " cmds=" + packet.getCommands().size() + " roots=" + names.size());
    }

    /**
     * Fallback: literal names merged onto vanilla essentials catalog.
     * Used when tree parse fails or only names are available.
     */
    public static void onCommands(LinkBedrockSession session, List<String> literalNames) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        LinkedHashSet<String> extras = new LinkedHashSet<>();
        if (literalNames != null) {
            for (String name : literalNames) {
                if (name != null && !name.isBlank() && name.length() <= 32) {
                    extras.add(name.toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        extras.addAll(session.pluginCommandNames());
        if (extras.isEmpty()) {
            return;
        }
        session.rememberPluginCommands(extras);
        AvailableCommandsPacket packet = LinkJoinPackets.availableCommandsVanillaPlus(extras);
        session.sendUpstreamPacket(packet);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_commands→be AvailableCommands extras=" + extras.size()
                        + " total=" + packet.getCommands().size() + " mode=literals");
        LOG.info("BE AvailableCommands refresh user=" + session.username()
                + " extras=" + extras.size() + " total=" + packet.getCommands().size());
    }

    public static List<String> merge(List<String> a, List<String> b) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (a != null) {
            out.addAll(a);
        }
        if (b != null) {
            out.addAll(b);
        }
        return new ArrayList<>(out);
    }
}

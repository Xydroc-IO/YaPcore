package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.cloudburst.LinkJoinPackets;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;

/**
 * JE {@code commands} packet → refresh Bedrock {@link AvailableCommandsPacket} with plugin literals.
 *
 * <p>Typed {@code /plugincommand} already reaches Folia via {@link BedrockCommandTranslator};
 * this only expands the Bedrock command enum / autocomplete list.
 */
public final class JavaCommandsTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private JavaCommandsTranslator() {
    }

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
                        + " total=" + packet.getCommands().size());
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

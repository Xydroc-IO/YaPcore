package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.Locale;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;

/**
 * Bedrock CommandRequest → Link proxy commands (/hub, /server) or JE chat_command.
 */
public final class BedrockCommandTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private BedrockCommandTranslator() {
    }

    public static void translate(LinkBedrockSession session, CommandRequestPacket cmd) {
        if (session == null || cmd == null) {
            return;
        }
        String line = cmd.getCommand();
        if (line == null || line.isBlank()) {
            line = "";
        } else {
            line = line.trim();
        }
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        String lower = line.toLowerCase(Locale.ROOT);
        String base = lower;
        String arg = null;
        int sp = lower.indexOf(' ');
        if (sp > 0) {
            base = lower.substring(0, sp);
            arg = line.substring(sp + 1).trim();
        }

        // Link plugin commands — must soft-switch here; Folia has no /hub proxy.
        if ("hub".equals(base) || "lobby".equals(base)) {
            if (session.requestBackendSwitch("lobby")) {
                BedrockJoinProbe.noteEvent(session.guid(), "be_command→link /" + base + " → lobby");
                LOG.info("BE→Link /" + base + " user=" + session.username());
            } else {
                BedrockJoinProbe.noteEvent(session.guid(), "be_command→link FAIL /" + base);
                LOG.warning("BE /" + base + " soft-switch unavailable user=" + session.username());
            }
            return;
        }
        if ("server".equals(base) && arg != null && !arg.isBlank()) {
            String target = arg.split("\\s+")[0];
            if (session.requestBackendSwitch(target)) {
                BedrockJoinProbe.noteEvent(session.guid(),
                        "be_command→link /server " + target);
                LOG.info("BE→Link /server " + target + " user=" + session.username());
            } else {
                BedrockJoinProbe.noteEvent(session.guid(),
                        "be_command→link FAIL /server " + target);
            }
            return;
        }

        JavaDownstreamClient down = session.downstream();
        if (down != null && down.phase() == JavaDownstreamClient.Phase.PLAY) {
            down.sendChatCommand(line);
        }
        // Do not echo "/cmd" back as system chat — Folia already replies and duplicates look wonky.
        BedrockJoinProbe.noteEvent(session.guid(), "be_command→je /" + line);
        LOG.info("BE→JE command user=" + session.username() + " /" + line);
    }
}

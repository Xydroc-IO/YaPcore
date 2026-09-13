package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;

/**
 * Bedrock CommandRequest → JE chat_command on the Java downstream.
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
        JavaDownstreamClient down = session.downstream();
        if (down != null && down.phase() == JavaDownstreamClient.Phase.PLAY) {
            down.sendChatCommand(line);
        }
        // Do not echo "/cmd" back as system chat — Folia already replies and duplicates look wonky.
        BedrockJoinProbe.noteEvent(session.guid(), "be_command→je /" + line);
        LOG.info("BE→JE command user=" + session.username() + " /" + line);
    }
}

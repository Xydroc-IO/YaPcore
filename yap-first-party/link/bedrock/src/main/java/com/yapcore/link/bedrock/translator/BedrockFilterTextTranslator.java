package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.FilterTextPacket;

/**
 * Bedrock {@link FilterTextPacket} → echo + JE {@code rename_item} when anvil is open.
 *
 * <p>Clients require a {@code fromServer=true} echo before accepting rename text.
 */
public final class BedrockFilterTextTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    /** JE MenuType id for anvil (see {@link JavaOpenScreenTranslator}). */
    private static final int JE_MENU_ANVIL = 7;

    private BedrockFilterTextTranslator() {
    }

    public static void translate(LinkBedrockSession session, FilterTextPacket packet) {
        if (session == null || packet == null || packet.isFromServer()) {
            return;
        }
        if (session.joinPhase() != LinkBedrockSession.JoinPhase.SPAWNED) {
            return;
        }
        String text = packet.getText() == null ? "" : packet.getText();
        FilterTextPacket echo = new FilterTextPacket();
        echo.setText(text);
        echo.setFromServer(true);
        session.sendUpstreamPacket(echo);

        if (session.lastJeMenuType() == JE_MENU_ANVIL) {
            JavaDownstreamClient down = session.downstream();
            if (down != null && down.phase() == JavaDownstreamClient.Phase.PLAY) {
                down.sendRenameItem(text);
                LOG.fine("BE→JE anvil rename user=" + session.username() + " text=" + text);
                BedrockJoinProbe.noteEvent(session.guid(), "BE FilterText→rename_item len=" + text.length());
            }
        } else {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "BE FilterText echo menu=" + session.lastJeMenuType() + " len=" + text.length());
        }
    }
}

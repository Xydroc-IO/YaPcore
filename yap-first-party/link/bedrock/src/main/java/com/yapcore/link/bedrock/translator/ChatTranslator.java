package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;

/**
 * Bedrock Text ↔ JE chat / system_chat (best-effort).
 */
public final class ChatTranslator {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private ChatTranslator() {
    }

    public static void bedrockToJava(LinkBedrockSession session, TextPacket text) {
        if (session == null || text == null) {
            return;
        }
        if (text.getType() != TextPacket.Type.CHAT && text.getType() != TextPacket.Type.WHISPER) {
            return;
        }
        String msg = text.getMessage();
        if (msg == null || msg.isBlank()) {
            return;
        }
        JavaDownstreamClient down = session.downstream();
        if (down == null || down.phase() != JavaDownstreamClient.Phase.PLAY) {
            return;
        }
        if (msg.startsWith("/")) {
            down.sendChatCommand(msg.substring(1));
        } else {
            down.sendChatMessage(msg);
        }
        BedrockJoinProbe.noteEvent(session.guid(), "be_chat→je len=" + msg.length());
        LOG.info("BE→JE chat user=" + session.username() + " msg=" + truncate(msg));
    }

    public static void javaSystemChatToBedrock(LinkBedrockSession session, String plain) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        plain = sanitize(plain);
        if (!isDisplayableChat(plain)) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_system_chat DROP garbage='" + truncate(String.valueOf(plain)) + "'");
            return;
        }
        TextPacket packet = new TextPacket();
        packet.setType(TextPacket.Type.SYSTEM);
        packet.setNeedsTranslation(false);
        packet.setSourceName("");
        packet.setMessage(plain);
        packet.setXuid("");
        packet.setPlatformChatId("");
        packet.setFilteredMessage("");
        session.sendUpstreamPacket(packet);
        BedrockJoinProbe.noteEvent(session.guid(), "java_system_chat→be len=" + plain.length());
    }

    public static void javaPlayerChatToBedrock(LinkBedrockSession session, String source, String plain) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        plain = sanitize(plain);
        if (!isDisplayableChat(plain)) {
            BedrockJoinProbe.noteEvent(session.guid(),
                    "java_player_chat DROP garbage='" + truncate(String.valueOf(plain)) + "'");
            return;
        }
        String src = source == null ? "" : source;
        // Prefer display name over raw UUID strings.
        if (src.length() == 36 && src.indexOf('-') == 8) {
            try {
                java.util.UUID u = java.util.UUID.fromString(src);
                String remembered = session.playerName(u);
                if (remembered != null && !remembered.isBlank()) {
                    src = remembered;
                } else {
                    src = "Player";
                }
            } catch (Exception ignored) {
                // keep
            }
        }
        TextPacket packet = new TextPacket();
        packet.setType(TextPacket.Type.CHAT);
        packet.setNeedsTranslation(false);
        packet.setSourceName(src);
        packet.setMessage(plain);
        packet.setXuid("");
        packet.setPlatformChatId("");
        packet.setFilteredMessage("");
        session.sendUpstreamPacket(packet);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_player_chat→be src=" + src + " len=" + plain.length());
    }

    /**
     * Reject raw NBT/varint mis-decodes that showed as digit spam ({@code 8}/{@code 0}/{@code 0}).
     * TAG_String type byte is 8; TAG_End is 0 — never treat those as chat text.
     */
    static boolean isDisplayableChat(String plain) {
        if (plain == null || plain.isBlank()) {
            return false;
        }
        if ("[chat]".equals(plain)) {
            return false;
        }
        String t = plain.trim();
        // Single/double digit-only lines are almost always misparsed packet fields.
        if (t.length() <= 4 && t.chars().allMatch(Character::isDigit)) {
            return false;
        }
        // Partial JSON / NBT fragments.
        if (t.startsWith("{") || t.startsWith("\"") || t.startsWith("[")) {
            return false;
        }
        if (t.contains("\"text\"") || t.contains("\"translate\"")) {
            return false;
        }
        // Raw translation keys that slipped through the component formatter.
        if (t.startsWith("death.") || t.startsWith("entity.") || t.startsWith("chat.")
                || t.contains("death.attack.") || t.contains("entity.minecraft.")) {
            return false;
        }
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (c == 0 || (c < 0x20 && c != '\n' && c != '\r' && c != '\t')) {
                return false;
            }
        }
        return true;
    }

    /** Strip leading BOM / trim; collapse runaway whitespace. */
    private static String sanitize(String plain) {
        if (plain == null) {
            return null;
        }
        String s = plain.replace('\u0000', ' ').trim();
        if (s.length() > 256) {
            s = s.substring(0, 256);
        }
        return s;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 64 ? s : s.substring(0, 64) + "…";
    }
}

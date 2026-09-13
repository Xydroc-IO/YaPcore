package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;

/**
 * ModalFormResponse → log + optional FormService hook stub that acknowledges.
 */
public final class BedrockFormBridge {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    /** Optional hook: (username, formJsonOrNull) — e.g. future YaP FormService. */
    private static volatile BiConsumer<String, String> formServiceHook;

    private static final ConcurrentHashMap<String, Integer> lastFormByUser = new ConcurrentHashMap<>();

    private BedrockFormBridge() {
    }

    public static void setFormServiceHook(BiConsumer<String, String> hook) {
        formServiceHook = hook;
    }

    public static void translate(LinkBedrockSession session, ModalFormResponsePacket form) {
        if (session == null || form == null) {
            return;
        }
        int formId = form.getFormId();
        String data = form.getFormData();
        boolean cancelled = data == null || "null".equals(data);
        lastFormByUser.put(session.username(), formId);
        LOG.info("BE ModalFormResponse user=" + session.username()
                + " formId=" + formId
                + " cancelled=" + cancelled
                + " data=" + (cancelled ? "null" : truncate(Objects.toString(data, ""))));
        BedrockJoinProbe.noteEvent(session.guid(),
                "be_form_response id=" + formId + " cancelled=" + cancelled);

        BiConsumer<String, String> hook = formServiceHook;
        if (hook != null) {
            try {
                hook.accept(session.username(), cancelled ? null : data);
            } catch (Exception e) {
                LOG.warning("BE FormService hook failed: " + e.getMessage());
            }
        }

        // Acknowledge so callers/clients know the response was accepted.
        TextPacket ack = new TextPacket();
        ack.setType(TextPacket.Type.SYSTEM);
        ack.setNeedsTranslation(false);
        ack.setSourceName("YaP Link");
        ack.setMessage(cancelled ? "§7Form cancelled." : "§7Form received.");
        ack.setXuid("");
        ack.setPlatformChatId("");
        ack.setFilteredMessage("");
        session.sendUpstreamPacket(ack);
    }

    public static Integer lastFormId(String username) {
        return username == null ? null : lastFormByUser.get(username);
    }

    private static String truncate(String s) {
        return s.length() <= 96 ? s : s.substring(0, 96) + "…";
    }
}

package com.yapcore.link.bedrock.translator;

import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.downstream.JavaPlayInventoryWire;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.session.LinkBedrockSession;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;

/**
 * Floodgate {@code floodgate:form} ↔ Bedrock {@link ModalFormRequestPacket} /
 * {@link ModalFormResponsePacket}.
 */
public final class BedrockFormBridge {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");
    public static final String FLOODGATE_FORM_CHANNEL = "floodgate:form";

    /** Optional hook: (username, formJsonOrNull) — e.g. YaP FormService observers. */
    private static volatile BiConsumer<String, String> formServiceHook;

    private static final ConcurrentHashMap<String, Integer> lastFormByUser = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Integer> pendingFormByGuid = new ConcurrentHashMap<>();
    private static final AtomicInteger localFormIds = new AtomicInteger(1000);

    private BedrockFormBridge() {
    }

    public static void setFormServiceHook(BiConsumer<String, String> hook) {
        formServiceHook = hook;
    }

    /** JE custom_payload {@code floodgate:form} → ModalFormRequest to Bedrock client. */
    public static void onJavaCustomPayload(LinkBedrockSession session, String channel, byte[] data) {
        if (session == null || channel == null || data == null) {
            return;
        }
        if (!FLOODGATE_FORM_CHANNEL.equals(channel) && !"floodgate:form".equalsIgnoreCase(channel)) {
            return;
        }
        if (data.length < 3) {
            return;
        }
        // Outbound Floodgate: typeOrdinal, formId BE short, UTF-8 JSON
        byte type = data[0];
        short formId = (short) (((data[1] & 0xff) << 8) | (data[2] & 0xff));
        String json = new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        sendModalForm(session, formId & 0xffff, json);
        BedrockJoinProbe.noteEvent(session.guid(),
                "java_floodgate_form→be id=" + (formId & 0xffff) + " type=" + type
                        + " jsonLen=" + json.length());
        LOG.info("BE ModalFormRequest from floodgate id=" + (formId & 0xffff)
                + " user=" + session.username());
    }

    /** Send a Cumulus-compatible JSON form to the Bedrock client. */
    public static int sendModalForm(LinkBedrockSession session, String json) {
        int id = localFormIds.getAndIncrement();
        sendModalForm(session, id, json);
        return id;
    }

    public static void sendModalForm(LinkBedrockSession session, int formId, String json) {
        if (session == null || !session.isSentSpawnPacket()) {
            return;
        }
        ModalFormRequestPacket req = new ModalFormRequestPacket();
        req.setFormId(formId);
        req.setFormData(json != null ? json : "{\"type\":\"modal\",\"title\":\"\",\"content\":\"\",\"button1\":\"OK\",\"button2\":\"Cancel\"}");
        session.sendUpstreamPacket(req);
        pendingFormByGuid.put(session.guid(), formId);
        lastFormByUser.put(session.username(), formId);
        BedrockJoinProbe.noteEvent(session.guid(), "be_ModalFormRequest id=" + formId);
    }

    public static void translate(LinkBedrockSession session, ModalFormResponsePacket form) {
        if (session == null || form == null) {
            return;
        }
        int formId = form.getFormId();
        String data = form.getFormData();
        boolean cancelled = data == null || "null".equals(data);
        lastFormByUser.put(session.username(), formId);
        pendingFormByGuid.remove(session.guid(), formId);
        LOG.info("BE ModalFormResponse user=" + session.username()
                + " formId=" + formId
                + " cancelled=" + cancelled
                + " data=" + (cancelled ? "null" : truncate(Objects.toString(data, ""))));
        BedrockJoinProbe.noteEvent(session.guid(),
                "be_form_response id=" + formId + " cancelled=" + cancelled);

        // Forward to Folia Floodgate plugin channel so plugin UIs complete.
        JavaDownstreamClient down = session.downstream();
        if (down != null && down.phase() == JavaDownstreamClient.Phase.PLAY) {
            byte[] payload = JavaPlayInventoryWire.floodgateFormResponse(
                    (short) formId, cancelled ? null : data);
            down.sendCustomPayload(FLOODGATE_FORM_CHANNEL, payload);
            BedrockJoinProbe.noteEvent(session.guid(),
                    "be_form→je floodgate:form id=" + formId + " bytes=" + payload.length);
        }

        BiConsumer<String, String> hook = formServiceHook;
        if (hook != null) {
            try {
                hook.accept(session.username(), cancelled ? null : data);
            } catch (Exception e) {
                LOG.warning("BE FormService hook failed: " + e.getMessage());
            }
        }
    }

    public static Integer lastFormId(String username) {
        return username == null ? null : lastFormByUser.get(username);
    }

    private static String truncate(String s) {
        return s.length() <= 96 ? s : s.substring(0, 96) + "…";
    }
}

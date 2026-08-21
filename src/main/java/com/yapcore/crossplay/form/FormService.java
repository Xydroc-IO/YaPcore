package com.yapcore.crossplay.form;

import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import io.netty.buffer.ByteBuf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Bedrock modal forms (Geyser parity 4.G4).
 */
public final class FormService {

    private static final Logger LOG = Logger.getLogger("YaPcore.Form");

    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, PendingForm> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastResponse = new ConcurrentHashMap<>();
    private BiConsumer<String, ByteBuf> sender = (user, buf) -> {
    };

    public record PendingForm(int id, String username, String type, String json) {
    }

    public void setSender(BiConsumer<String, ByteBuf> sender) {
        this.sender = sender != null ? sender : (u, b) -> {
        };
    }

    public int sendSimple(String username, String title, String content, String... buttons) {
        StringBuilder btns = new StringBuilder("[");
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0) {
                btns.append(',');
            }
            btns.append("{\"text\":\"").append(escape(buttons[i])).append("\"}");
        }
        btns.append(']');
        String json = "{\"type\":\"form\",\"title\":\"" + escape(title)
                + "\",\"content\":\"" + escape(content) + "\",\"buttons\":" + btns + "}";
        return send(username, "form", json);
    }

    public int sendModal(String username, String title, String content, String button1, String button2) {
        String json = "{\"type\":\"modal\",\"title\":\"" + escape(title)
                + "\",\"content\":\"" + escape(content)
                + "\",\"button1\":\"" + escape(button1)
                + "\",\"button2\":\"" + escape(button2) + "\"}";
        return send(username, "modal", json);
    }

    public int sendCustom(String username, String title, String jsonContentArray) {
        String json = "{\"type\":\"custom_form\",\"title\":\"" + escape(title)
                + "\",\"content\":" + jsonContentArray + "}";
        return send(username, "custom_form", json);
    }

    private int send(String username, String type, String json) {
        int id = nextId.getAndIncrement();
        pending.put(id, new PendingForm(id, username, type, json));
        ByteBuf pkt = BedrockPacketCodec.modalFormRequest(id, json);
        sender.accept(username, pkt);
        LOG.info("Form #" + id + " → " + username + " type=" + type);
        return id;
    }

    public void handleResponse(String username, ByteBuf body) {
        try {
            int formId = BedrockPacketCodec.readUnsignedVarInt(body);
            boolean hasData = body.isReadable() && body.readBoolean();
            String data = hasData && body.isReadable() ? BedrockPacketCodec.readString(body) : "null";
            pending.remove(formId);
            lastResponse.put(username.toLowerCase(), formId + "|" + data);
            LOG.info("Form response " + username + " #" + formId + " data=" + data);
        } catch (Exception e) {
            LOG.fine("Form response parse: " + e.getMessage());
        }
    }

    public String lastResponse(String username) {
        return lastResponse.get(username.toLowerCase());
    }

    public Map<Integer, PendingForm> pendingSnapshot() {
        return Map.copyOf(pending);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

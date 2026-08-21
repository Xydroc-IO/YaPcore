package com.yapcore.crossplay.form;

import com.yapcore.crossplay.bedrock.BedrockPacketCodec;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Bedrock modal forms (Geyser / Cumulus-class parity — 4.G4 / P4.8).
 * Supports simple, modal, and custom forms with typed content + result handlers.
 */
public final class FormService {

    private static final Logger LOG = Logger.getLogger("YaPcore.Form");

    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, PendingForm> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastResponse = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Consumer<FormResult>> handlers = new ConcurrentHashMap<>();
    private BiConsumer<String, ByteBuf> sender = (user, buf) -> {
    };

    public record PendingForm(int id, String username, String type, String json) {
    }

    public record FormResult(int formId, String username, String rawData, boolean closed) {
        public boolean cancelled() {
            return closed || rawData == null || "null".equals(rawData);
        }
    }

    public void setSender(BiConsumer<String, ByteBuf> sender) {
        this.sender = sender != null ? sender : (u, b) -> {
        };
    }

    public int sendSimple(String username, String title, String content, String... buttons) {
        return sendSimple(username, title, content, null, buttons);
    }

    public int sendSimple(String username, String title, String content,
                          Consumer<FormResult> onResult, String... buttons) {
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
        return send(username, "form", json, onResult);
    }

    public int sendModal(String username, String title, String content, String button1, String button2) {
        return sendModal(username, title, content, button1, button2, null);
    }

    public int sendModal(String username, String title, String content,
                         String button1, String button2, Consumer<FormResult> onResult) {
        String json = "{\"type\":\"modal\",\"title\":\"" + escape(title)
                + "\",\"content\":\"" + escape(content)
                + "\",\"button1\":\"" + escape(button1)
                + "\",\"button2\":\"" + escape(button2) + "\"}";
        return send(username, "modal", json, onResult);
    }

    public int sendCustom(String username, String title, String jsonContentArray) {
        return sendCustom(username, title, jsonContentArray, null);
    }

    public int sendCustom(String username, String title, String jsonContentArray,
                          Consumer<FormResult> onResult) {
        String json = "{\"type\":\"custom_form\",\"title\":\"" + escape(title)
                + "\",\"content\":" + jsonContentArray + "}";
        return send(username, "custom_form", json, onResult);
    }

    /** Fluent custom form builder (label / input / toggle / slider / dropdown / step_slider). */
    public CustomFormBuilder custom(String username, String title) {
        return new CustomFormBuilder(this, username, title);
    }

    public static final class CustomFormBuilder {
        private final FormService service;
        private final String username;
        private final String title;
        private final List<String> elements = new ArrayList<>();
        private Consumer<FormResult> onResult;

        private CustomFormBuilder(FormService service, String username, String title) {
            this.service = service;
            this.username = username;
            this.title = title;
        }

        public CustomFormBuilder label(String text) {
            elements.add("{\"type\":\"label\",\"text\":\"" + escape(text) + "\"}");
            return this;
        }

        public CustomFormBuilder input(String text, String placeholder, String defaultValue) {
            elements.add("{\"type\":\"input\",\"text\":\"" + escape(text)
                    + "\",\"placeholder\":\"" + escape(placeholder)
                    + "\",\"default\":\"" + escape(defaultValue) + "\"}");
            return this;
        }

        public CustomFormBuilder toggle(String text, boolean defaultValue) {
            elements.add("{\"type\":\"toggle\",\"text\":\"" + escape(text)
                    + "\",\"default\":" + defaultValue + "}");
            return this;
        }

        public CustomFormBuilder slider(String text, float min, float max, float step, float def) {
            elements.add("{\"type\":\"slider\",\"text\":\"" + escape(text)
                    + "\",\"min\":" + min + ",\"max\":" + max + ",\"step\":" + step
                    + ",\"default\":" + def + "}");
            return this;
        }

        public CustomFormBuilder dropdown(String text, String... options) {
            StringBuilder opts = new StringBuilder("[");
            for (int i = 0; i < options.length; i++) {
                if (i > 0) {
                    opts.append(',');
                }
                opts.append('"').append(escape(options[i])).append('"');
            }
            opts.append(']');
            elements.add("{\"type\":\"dropdown\",\"text\":\"" + escape(text)
                    + "\",\"options\":" + opts + ",\"default\":0}");
            return this;
        }

        public CustomFormBuilder onResult(Consumer<FormResult> handler) {
            this.onResult = handler;
            return this;
        }

        public int send() {
            return service.sendCustom(username, title, "[" + String.join(",", elements) + "]", onResult);
        }
    }

    private int send(String username, String type, String json, Consumer<FormResult> onResult) {
        int id = nextId.getAndIncrement();
        pending.put(id, new PendingForm(id, username, type, json));
        if (onResult != null) {
            handlers.put(id, onResult);
        }
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
            Consumer<FormResult> h = handlers.remove(formId);
            FormResult result = new FormResult(formId, username, data, "null".equals(data));
            if (h != null) {
                try {
                    h.accept(result);
                } catch (Exception e) {
                    LOG.fine("Form handler error: " + e.getMessage());
                }
            }
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

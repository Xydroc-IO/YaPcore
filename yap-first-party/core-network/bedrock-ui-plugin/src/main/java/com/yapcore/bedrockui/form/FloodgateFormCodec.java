package com.yapcore.bedrockui.form;

import java.nio.charset.StandardCharsets;

/**
 * Stock Floodgate/Geyser {@code floodgate:form} wire codec + Cumulus-compatible JSON builders.
 *
 * <p>Outbound: {@code byte typeOrdinal} (0=simple/form, 1=modal, 2=custom), {@code short formId} BE,
 * UTF-8 JSON. Inbound: {@code short formId} BE, UTF-8 response (or empty / formId-only = close).
 */
public final class FloodgateFormCodec {

    public static final byte TYPE_SIMPLE = 0;
    public static final byte TYPE_MODAL = 1;
    public static final byte TYPE_CUSTOM = 2;

    private FloodgateFormCodec() {
    }

    public static String simpleJson(String title, String content, String... buttons) {
        StringBuilder btns = new StringBuilder("[");
        String[] safe = buttons != null ? buttons : new String[0];
        for (int i = 0; i < safe.length; i++) {
            if (i > 0) {
                btns.append(',');
            }
            btns.append("{\"text\":\"").append(escape(safe[i])).append("\"}");
        }
        btns.append(']');
        return "{\"type\":\"form\",\"title\":\"" + escape(title)
                + "\",\"content\":\"" + escape(content) + "\",\"buttons\":" + btns + "}";
    }

    public static String modalJson(String title, String content, String button1, String button2) {
        return "{\"type\":\"modal\",\"title\":\"" + escape(title)
                + "\",\"content\":\"" + escape(content)
                + "\",\"button1\":\"" + escape(button1)
                + "\",\"button2\":\"" + escape(button2) + "\"}";
    }

    public static String customJson(String title, String jsonContentArray) {
        String content = jsonContentArray == null || jsonContentArray.isBlank() ? "[]" : jsonContentArray;
        return "{\"type\":\"custom_form\",\"title\":\"" + escape(title)
                + "\",\"content\":" + content + "}";
    }

    /** Encode server → client form payload. */
    public static byte[] encodeOutbound(byte typeOrdinal, short formId, String json) {
        byte[] jsonBytes = (json == null ? "" : json).getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[3 + jsonBytes.length];
        data[0] = typeOrdinal;
        data[1] = (byte) ((formId >> 8) & 0xFF);
        data[2] = (byte) (formId & 0xFF);
        System.arraycopy(jsonBytes, 0, data, 3, jsonBytes.length);
        return data;
    }

    public record InboundResponse(short formId, String rawData, boolean closed) {
    }

    /** Decode client → server form response. */
    public static InboundResponse decodeInbound(byte[] data) {
        if (data == null || data.length < 2) {
            throw new IllegalArgumentException("form response too short");
        }
        short formId = (short) (((data[0] & 0xFF) << 8) | (data[1] & 0xFF));
        if (data.length == 2) {
            return new InboundResponse(formId, "null", true);
        }
        String raw = new String(data, 2, data.length - 2, StandardCharsets.UTF_8);
        if (raw.isEmpty() || "null".equals(raw)) {
            return new InboundResponse(formId, "null", true);
        }
        return new InboundResponse(formId, raw, false);
    }

    public static short readFormId(byte[] data) {
        if (data == null || data.length < 2) {
            throw new IllegalArgumentException("form id missing");
        }
        return (short) (((data[0] & 0xFF) << 8) | (data[1] & 0xFF));
    }

    static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

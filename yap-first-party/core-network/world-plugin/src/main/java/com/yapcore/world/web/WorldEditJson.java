package com.yapcore.world.web;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal JSON for the world-edit HTTP API (no external deps). */
final class WorldEditJson {

    private WorldEditJson() {
    }

    static byte[] object(Map<String, ?> map) {
        return encodeObject(map).getBytes(StandardCharsets.UTF_8);
    }

    static String parseAction(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        for (String part : body.replace("{", "").replace("}", "").split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2 && kv[0].trim().replace("\"", "").equals("action")) {
                return unquote(kv[1].trim());
            }
        }
        return "";
    }

    static String parseField(String body, String key) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String needle = "\"" + key + "\"";
        int idx = body.indexOf(needle);
        if (idx < 0) {
            return "";
        }
        int colon = body.indexOf(':', idx);
        if (colon < 0) {
            return "";
        }
        int start = colon + 1;
        while (start < body.length() && Character.isWhitespace(body.charAt(start))) {
            start++;
        }
        if (start >= body.length()) {
            return "";
        }
        if (body.charAt(start) == '"') {
            int end = body.indexOf('"', start + 1);
            return end < 0 ? "" : body.substring(start + 1, end);
        }
        int end = start;
        while (end < body.length() && ",}".indexOf(body.charAt(end)) < 0) {
            end++;
        }
        return body.substring(start, end).trim();
    }

    private static String encodeObject(Map<String, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            sb.append(encodeValue(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    private static String encodeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (value instanceof Number n) {
            return n.toString();
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                copy.put(String.valueOf(e.getKey()), e.getValue());
            }
            return encodeObject(copy);
        }
        if (value instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object o : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(encodeValue(o));
            }
            sb.append(']');
            return sb.toString();
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unquote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}

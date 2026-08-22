package com.yapcore.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal JSON encode/decode — no extra dependencies. */
public final class TinyJson {

    static final Pattern ENTRY = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*(null|true|false|-?\\d+(?:\\.\\d+)?|\"(?:\\\\.|[^\"\\\\])*\")");

    private TinyJson() {
    }

    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String str(String s) {
        return "\"" + esc(s) + "\"";
    }

    public static String obj(Map<String, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(str(e.getKey())).append(':').append(val(e.getValue()));
        }
        return sb.append('}').toString();
    }

    public static String arr(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(val(list.get(i)));
        }
        return sb.append(']').toString();
    }

    @SuppressWarnings("unchecked")
    private static String val(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String s) {
            return str(s);
        }
        if (v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        if (v instanceof Map<?, ?> m) {
            return obj((Map<String, ?>) m);
        }
        if (v instanceof List<?> l) {
            return arr(l);
        }
        return str(v.toString());
    }

    public static Object parseValue(String raw) {
        if ("null".equals(raw)) {
            return null;
        }
        if ("true".equals(raw)) {
            return true;
        }
        if ("false".equals(raw)) {
            return false;
        }
        if (raw.startsWith("\"")) {
            return unescape(raw.substring(1, raw.length() - 1));
        }
        if (raw.contains(".")) {
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException ignored) {
                return raw;
            }
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    /** Flat string-valued object parser (enough for dashboard POSTs). */
    public static Map<String, String> parseFlatObject(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        Matcher m = ENTRY.matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String raw = m.group(2);
            if ("null".equals(raw)) {
                out.put(key, "");
            } else if (raw.startsWith("\"")) {
                out.put(key, unescape(raw.substring(1, raw.length() - 1)));
            } else {
                out.put(key, raw);
            }
        }
        return out;
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(switch (n) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> n;
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static List<Map<String, Object>> listOfMaps() {
        return new ArrayList<>();
    }
}

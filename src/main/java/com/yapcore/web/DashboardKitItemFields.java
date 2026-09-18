package com.yapcore.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared kit item field helpers for {@link DashboardKitItems}. */
final class DashboardKitItemFields {

    private DashboardKitItemFields() {
    }

    static String joinLore(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> lines = new ArrayList<>();
            for (Object line : list) {
                if (line != null && !String.valueOf(line).isBlank()) {
                    lines.add(String.valueOf(line));
                }
            }
            return String.join(";", lines);
        }
        return raw == null ? "" : String.valueOf(raw);
    }

    static List<String> splitLore(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(";")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static String joinEnchants(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (var e : map.entrySet()) {
                parts.add(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT) + ":" + intVal(e.getValue(), 1));
            }
            return String.join(",", parts);
        }
        return raw == null ? "" : String.valueOf(raw);
    }

    static Map<String, Integer> splitEnchants(String raw) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            String[] kv = t.split(":", 2);
            String key = kv[0].trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            if (key.startsWith("minecraft:")) {
                key = key.substring("minecraft:".length());
            }
            if (!key.isEmpty()) {
                out.put(key, kv.length > 1 ? parseAmount(kv[1]) : 1);
            }
        }
        return out;
    }

    static String normalizeSlot(String raw) {
        return switch (raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)) {
            case "helmet", "head" -> "helmet";
            case "chest", "chestplate" -> "chestplate";
            case "legs", "leggings" -> "leggings";
            case "boots", "feet" -> "boots";
            case "offhand", "off-hand", "shield" -> "offhand";
            default -> "inventory";
        };
    }

    static String escapeField(String raw) {
        return raw.replace("\\", "\\\\").replace("|", "\\|").replace("\n", " ");
    }

    static String unescapeField(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                sb.append(raw.charAt(++i));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static List<String> stringList(Object val) {
        List<String> out = new ArrayList<>();
        if (val instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    out.add(String.valueOf(item));
                }
            }
        } else if (val instanceof String s && !s.isBlank()) {
            for (String part : s.split("\n")) {
                if (!part.isBlank()) {
                    out.add(part.trim());
                }
            }
        }
        return out;
    }

    static Object first(Map<String, Object> map, String a, String b) {
        if (map.containsKey(a)) {
            return map.get(a);
        }
        return map.get(b);
    }

    static String str(Object val, String fallback) {
        if (val == null) {
            return fallback;
        }
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? fallback : s;
    }

    static int parseAmount(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (Exception e) {
            return 1;
        }
    }

    static int intVal(Object val, int fallback) {
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(String.valueOf(val).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    static long longVal(Object val, long fallback) {
        if (val instanceof Number n) {
            return n.longValue();
        }
        if (val != null) {
            try {
                return Long.parseLong(String.valueOf(val).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    static double doubleVal(Object val, double fallback) {
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val != null) {
            try {
                return Double.parseDouble(String.valueOf(val).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    static boolean boolVal(Object val, boolean fallback) {
        if (val instanceof Boolean b) {
            return b;
        }
        if (val != null) {
            return Boolean.parseBoolean(String.valueOf(val));
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}

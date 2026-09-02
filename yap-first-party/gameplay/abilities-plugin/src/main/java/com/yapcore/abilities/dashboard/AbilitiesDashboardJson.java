package com.yapcore.abilities.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON helper for dashboard console export. */
public final class AbilitiesDashboardJson {

    private AbilitiesDashboardJson() {
    }

    public static String toJson(Map<String, Object> snap) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : snap.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            sb.append(valueJson(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String valueJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return toJson(copy);
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(valueJson(item));
            }
            sb.append(']');
            return sb.toString();
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

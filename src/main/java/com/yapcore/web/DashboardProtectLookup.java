package com.yapcore.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses structured protect lookup rows from {@code yapprotect dash-lookup} console output. */
public final class DashboardProtectLookup {

    private static final Pattern OBJECT = Pattern.compile("\\{[^{}]*\\}");

    private DashboardProtectLookup() {
    }

    public static List<Map<String, Object>> parseDashJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        int idx = raw.indexOf("DASH_JSON=");
        if (idx < 0) {
            return List.of();
        }
        String json = raw.substring(idx + "DASH_JSON=".length()).trim();
        int nl = json.indexOf('\n');
        if (nl >= 0) {
            json = json.substring(0, nl).trim();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        Matcher m = OBJECT.matcher(json);
        while (m.find()) {
            Map<String, String> flat = TinyJson.parseFlatObject(m.group());
            if (!flat.isEmpty()) {
                Map<String, Object> row = new LinkedHashMap<>(flat);
                if (flat.containsKey("id")) {
                    try {
                        row.put("id", Long.parseLong(flat.get("id")));
                    } catch (NumberFormatException ignored) {
                    }
                }
                for (String key : List.of("x", "y", "z")) {
                    if (flat.containsKey(key)) {
                        try {
                            row.put(key, Integer.parseInt(flat.get(key)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                if (flat.containsKey("epochMs")) {
                    try {
                        row.put("epochMs", Long.parseLong(flat.get("epochMs")));
                    } catch (NumberFormatException ignored) {
                    }
                }
                out.add(row);
            }
        }
        return out;
    }
}

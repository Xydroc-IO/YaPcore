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
    private static final Pattern NEXT_CURSOR = Pattern.compile("\"nextCursor\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NEXT_CURSOR_NULL = Pattern.compile("\"nextCursor\"\\s*:\\s*null");
    private static final Pattern HAS_MORE = Pattern.compile("\"hasMore\"\\s*:\\s*(true|false)");

    private DashboardProtectLookup() {
    }

    public static List<Map<String, Object>> parseDashJson(String raw) {
        return parsePage(raw).rows();
    }

    public static Page parsePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return Page.empty();
        }
        int idx = raw.indexOf("DASH_JSON=");
        if (idx < 0) {
            return Page.empty();
        }
        String json = raw.substring(idx + "DASH_JSON=".length()).trim();
        int nl = json.indexOf('\n');
        if (nl >= 0) {
            json = json.substring(0, nl).trim();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        Matcher m = OBJECT.matcher(json);
        while (m.find()) {
            String obj = m.group();
            // Skip envelope-only fragments that aren't row objects
            if (!obj.contains("\"id\"") || !obj.contains("\"changeType\"")) {
                continue;
            }
            Map<String, String> flat = TinyJson.parseFlatObject(obj);
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
                for (String boolKey : List.of("rolledBack", "restorable")) {
                    if (flat.containsKey(boolKey)) {
                        row.put(boolKey, !"false".equalsIgnoreCase(flat.get(boolKey)));
                    }
                }
                out.add(row);
            }
        }
        String nextCursor = null;
        Matcher cursorMatch = NEXT_CURSOR.matcher(json);
        if (cursorMatch.find()) {
            nextCursor = cursorMatch.group(1);
        } else if (NEXT_CURSOR_NULL.matcher(json).find()) {
            nextCursor = null;
        }
        boolean hasMore = false;
        Matcher moreMatch = HAS_MORE.matcher(json);
        if (moreMatch.find()) {
            hasMore = "true".equalsIgnoreCase(moreMatch.group(1));
        }
        return new Page(out, nextCursor, hasMore);
    }

    public record Page(List<Map<String, Object>> rows, String nextCursor, boolean hasMore) {
        static Page empty() {
            return new Page(List.of(), null, false);
        }
    }
}

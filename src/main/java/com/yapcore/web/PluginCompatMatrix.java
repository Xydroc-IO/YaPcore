package com.yapcore.web;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Curated third-party plugin compatibility matrix (Phase 16).
 * Loaded from {@code plugin-compat-matrix.json} on classpath.
 */
public final class PluginCompatMatrix {

    public record Entry(
            String id,
            String jarPattern,
            String status,
            String nativeAlternative,
            String note
    ) {
        boolean matches(String fileName) {
            if (fileName == null || fileName.isBlank()) {
                return false;
            }
            String lower = fileName.toLowerCase(Locale.ROOT);
            String pat = jarPattern.toLowerCase(Locale.ROOT);
            if (pat.contains("*")) {
                String regex = "^" + pat.replace(".", "\\.").replace("*", ".*") + "$";
                return lower.matches(regex);
            }
            return lower.equals(pat) || lower.contains(pat);
        }
    }

    public record Lookup(
            String status,
            String nativeAlternative,
            String note,
            String matchedId
    ) {
        public boolean hasWarning() {
            return status != null && !"works".equalsIgnoreCase(status) && !"unknown".equalsIgnoreCase(status);
        }
    }

    private static final List<Entry> ENTRIES = load();

    private PluginCompatMatrix() {
    }

    public static List<Entry> all() {
        return List.copyOf(ENTRIES);
    }

    public static Lookup lookup(String fileName) {
        for (Entry e : ENTRIES) {
            if (e.matches(fileName)) {
                return new Lookup(e.status(), e.nativeAlternative(), e.note(), e.id());
            }
        }
        return new Lookup("unknown", "", "Not in curated matrix — test on a copy first.", "");
    }

    public static List<Map<String, Object>> warningsForInstalled(Iterable<String> fileNames) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : fileNames) {
            Lookup l = lookup(name);
            if (l.hasWarning()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fileName", name);
                row.put("compatStatus", l.status());
                row.put("nativeAlternative", l.nativeAlternative());
                row.put("note", l.note());
                row.put("matchedId", l.matchedId());
                out.add(row);
            }
        }
        return out;
    }

    private static List<Entry> load() {
        try (InputStream in = PluginCompatMatrix.class.getResourceAsStream("/plugin-compat-matrix.json")) {
            if (in == null) {
                return List.of();
            }
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parseJson(raw);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Minimal JSON array parser — avoids extra deps in core jar. */
    @SuppressWarnings("unchecked")
    private static List<Entry> parseJson(String raw) {
        List<Entry> out = new ArrayList<>();
        // Expect [{ "id": "...", "jarPattern": "...", ... }, ...]
        int i = raw.indexOf('[');
        if (i < 0) {
            return out;
        }
        int depth = 0;
        int objStart = -1;
        for (int p = i; p < raw.length(); p++) {
            char c = raw.charAt(p);
            if (c == '{') {
                if (depth == 0) {
                    objStart = p;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    out.add(parseObject(raw.substring(objStart, p + 1)));
                    objStart = -1;
                }
            }
        }
        return out;
    }

    private static Entry parseObject(String obj) {
        return new Entry(
                str(obj, "id"),
                str(obj, "jarPattern"),
                str(obj, "status"),
                str(obj, "nativeAlternative"),
                str(obj, "note"));
    }

    private static String str(String obj, String key) {
        String needle = "\"" + key + "\"";
        int k = obj.indexOf(needle);
        if (k < 0) {
            return "";
        }
        int colon = obj.indexOf(':', k + needle.length());
        if (colon < 0) {
            return "";
        }
        int q1 = obj.indexOf('"', colon + 1);
        if (q1 < 0) {
            return "";
        }
        int q2 = obj.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return "";
        }
        return obj.substring(q1 + 1, q2);
    }
}

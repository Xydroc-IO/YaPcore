package com.yapcore.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parse YaPNpcs shop JSON exports for the web dashboard. */
public final class DashboardShopUtil {

    private static final Pattern SHOP_PREFIX = Pattern.compile("YAPSHOP_JSON:(\\{.*})", Pattern.DOTALL);
    private static final Pattern PRESETS_PREFIX = Pattern.compile("YAPSHOP_PRESETS:(\\[.*])", Pattern.DOTALL);
    private static final Pattern OBJECT = Pattern.compile("\\{([^{}]*)\\}");
    private static final Pattern STRING = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

    private DashboardShopUtil() {
    }

    public static Map<String, Object> parseShopJson(String consoleOutput) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("offers", List.of());
        if (consoleOutput == null || consoleOutput.isBlank()) {
            return out;
        }
        Matcher m = SHOP_PREFIX.matcher(consoleOutput);
        if (!m.find()) {
            return out;
        }
        String body = m.group(1);
        // top-level scalar fields
        Matcher em = TinyJson.ENTRY.matcher(body);
        while (em.find()) {
            String key = em.group(1);
            if ("offers".equals(key)) {
                continue;
            }
            out.put(key, TinyJson.parseValue(em.group(2)));
        }
        int offersAt = body.indexOf("\"offers\"");
        if (offersAt >= 0) {
            int arrStart = body.indexOf('[', offersAt);
            int arrEnd = body.lastIndexOf(']');
            if (arrStart >= 0 && arrEnd > arrStart) {
                out.put("offers", parseOfferArray(body.substring(arrStart, arrEnd + 1)));
            }
        }
        return out;
    }

    public static List<String> parsePresetsJson(String consoleOutput) {
        if (consoleOutput == null || consoleOutput.isBlank()) {
            return List.of();
        }
        Matcher m = PRESETS_PREFIX.matcher(consoleOutput);
        if (!m.find()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Matcher sm = STRING.matcher(m.group(1));
        while (sm.find()) {
            out.add(sm.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return out;
    }

    private static List<Map<String, Object>> parseOfferArray(String json) {
        List<Map<String, Object>> out = new ArrayList<>();
        Matcher om = OBJECT.matcher(json);
        while (om.find()) {
            Map<String, Object> row = new LinkedHashMap<>();
            Matcher em = TinyJson.ENTRY.matcher(om.group(1));
            while (em.find()) {
                row.put(em.group(1), TinyJson.parseValue(em.group(2)));
            }
            if (!row.isEmpty()) {
                out.add(row);
            }
        }
        return out;
    }
}

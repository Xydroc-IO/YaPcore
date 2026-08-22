package com.yapcore.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parse YaPRegions console JSON export for the web dashboard. */
public final class DashboardRegionUtil {

    private static final Pattern JSON_PREFIX = Pattern.compile("YAPREGION_JSON:(\\[.*])", Pattern.DOTALL);
    private static final Pattern OBJECT = Pattern.compile("\\{([^{}]*)\\}");

    private DashboardRegionUtil() {
    }

    public static List<Map<String, Object>> parseListJson(String consoleOutput) {
        if (consoleOutput == null || consoleOutput.isBlank()) {
            return List.of();
        }
        Matcher m = JSON_PREFIX.matcher(consoleOutput);
        if (!m.find()) {
            return List.of();
        }
        return parseArray(m.group(1));
    }

    private static List<Map<String, Object>> parseArray(String json) {
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

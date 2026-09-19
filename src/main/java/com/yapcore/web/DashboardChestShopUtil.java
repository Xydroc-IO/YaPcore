package com.yapcore.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parse `/shop list json` chest-shop export for the dashboard. */
public final class DashboardChestShopUtil {

    public static final String PREFIX = "YAPCHESTSHOP_JSON:";
    private static final Pattern ARRAY = Pattern.compile("YAPCHESTSHOP_JSON:(\\[.*])", Pattern.DOTALL);
    private static final Pattern OBJECT = Pattern.compile("YAPCHESTSHOP_JSON:(\\{.*})", Pattern.DOTALL);
    private static final Pattern ROW = Pattern.compile("\\{([^{}]*)\\}");

    private DashboardChestShopUtil() {
    }

    public static List<Map<String, Object>> parseList(String consoleOutput) {
        if (consoleOutput == null || consoleOutput.isBlank()) {
            return List.of();
        }
        Matcher m = ARRAY.matcher(consoleOutput);
        if (m.find()) {
            return parseRows(m.group(1));
        }
        Map<String, Object> one = parseOne(consoleOutput);
        return one.isEmpty() ? List.of() : List.of(one);
    }

    public static Map<String, Object> parseOne(String consoleOutput) {
        if (consoleOutput == null || consoleOutput.isBlank()) {
            return Map.of();
        }
        Matcher m = OBJECT.matcher(consoleOutput);
        if (!m.find()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = parseRows("[" + m.group(1) + "]");
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private static List<Map<String, Object>> parseRows(String json) {
        List<Map<String, Object>> out = new ArrayList<>();
        Matcher om = ROW.matcher(json);
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

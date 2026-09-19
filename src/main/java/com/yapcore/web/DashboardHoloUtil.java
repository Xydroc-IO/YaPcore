package com.yapcore.web;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parse `/yapholo list json` and `plugins/YaPHolo/holograms.yml` for the dashboard. */
public final class DashboardHoloUtil {

    public static final String PREFIX = "YAPHOLO_JSON:";
    private static final Pattern ARRAY = Pattern.compile("YAPHOLO_JSON:(\\[.*])", Pattern.DOTALL);
    private static final Pattern ROW = Pattern.compile("\\{([^{}]*)\\}");

    private DashboardHoloUtil() {
    }

    public static List<Map<String, Object>> parseListJson(String consoleOutput) {
        if (consoleOutput == null || consoleOutput.isBlank()) {
            return List.of();
        }
        Matcher m = ARRAY.matcher(consoleOutput);
        if (!m.find()) {
            return List.of();
        }
        return parseRows(m.group(1));
    }

    public static List<Map<String, Object>> fromYaml(Path root) {
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPHolo", "holograms.yml");
        Object holos = yaml.get("holograms");
        if (!(holos instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (var e : map.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", String.valueOf(e.getKey()));
            Object world = row.get("world");
            one.put("world", world == null ? "world" : String.valueOf(world));
            one.put("x", row.get("x") == null ? 0 : row.get("x"));
            one.put("y", row.get("y") == null ? 64 : row.get("y"));
            one.put("z", row.get("z") == null ? 0 : row.get("z"));
            Object view = row.get("view-distance");
            one.put("view", view == null ? 48 : view);
            Object attach = row.get("attach");
            one.put("attach", attach == null ? "" : String.valueOf(attach));
            Object clicks = row.get("clicks");
            if (clicks instanceof List<?> list) {
                StringBuilder joined = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        joined.append(' ');
                    }
                    joined.append(list.get(i) == null ? "" : String.valueOf(list.get(i)));
                }
                one.put("clicks", joined.toString());
            } else {
                one.put("clicks", clicks == null ? "" : String.valueOf(clicks));
            }
            Object perm = row.get("see-permission");
            one.put("perm", perm == null ? "" : String.valueOf(perm));
            Object pages = row.get("pages");
            if (pages instanceof List<?> pageList && !pageList.isEmpty()) {
                one.put("lines", joinPages(pageList));
                one.put("pages", pageList.size());
            } else {
                Object lines = row.get("lines");
                one.put("lines", joinLines(lines));
                one.put("pages", 1);
            }
            out.add(one);
        }
        return out;
    }

    static String joinLines(Object lines) {
        if (lines instanceof List<?> list) {
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    joined.append('|');
                }
                joined.append(list.get(i) == null ? "" : String.valueOf(list.get(i)));
            }
            return joined.toString();
        }
        return lines == null ? "" : String.valueOf(lines);
    }

    static String joinPages(List<?> pages) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) {
                out.append(";;");
            }
            out.append(joinLines(pages.get(i)));
        }
        return out.toString();
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

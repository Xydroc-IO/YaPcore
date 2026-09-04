package com.yapcore.web;

import com.yapcore.server.YaPcoreServer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Live {@code /map/markers.json} for the dashboard (and pack) map UI. */
public final class DashboardMapMarkers {

    private DashboardMapMarkers() {
    }

    public static String build(YaPcoreServer server, Path root) {
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPMap", "config.yml");
        Map<String, Object> markers = DashboardNetworkSnapshots.map(yaml.get("markers"));
        boolean showPlayers = DashboardNetworkSnapshots.bool(markers.get("players"), true);
        boolean showNpcs = DashboardNetworkSnapshots.bool(markers.get("npcs"), false);
        boolean showRegions = DashboardNetworkSnapshots.bool(markers.get("regions"), false);
        int poll = Math.max(2, DashboardNetworkSnapshots.intVal(markers.get("poll-seconds"), 5));

        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"players\":");
        appendPlayers(sb, showPlayers ? DashboardPlayerList.onlinePlayers(server) : List.of());
        sb.append(",\"npcs\":");
        appendNpcs(sb, showNpcs && server != null
                ? DashboardNpcUtil.parseListJson(server.executeCommand("npc list json"))
                : List.of());
        sb.append(",\"regions\":");
        appendRegions(sb, showRegions && server != null
                ? DashboardRegionUtil.parseListJson(server.executeCommand("region list json"))
                : List.of());
        sb.append(",\"showPlayers\":").append(showPlayers);
        sb.append(",\"showNpcs\":").append(showNpcs);
        sb.append(",\"showRegions\":").append(showRegions);
        sb.append(",\"pollSeconds\":").append(poll);
        sb.append('}');
        return sb.toString();
    }

    private static void appendPlayers(StringBuilder sb, List<Map<String, Object>> players) {
        sb.append('[');
        boolean first = true;
        for (Map<String, Object> p : players) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('{')
                    .append("\"name\":").append(q(str(p.get("name")))).append(',')
                    .append("\"world\":").append(q(str(p.get("world")))).append(',')
                    .append("\"x\":").append(num(p.get("x"))).append(',')
                    .append("\"y\":").append(num(p.get("y"))).append(',')
                    .append("\"z\":").append(num(p.get("z")))
                    .append('}');
        }
        sb.append(']');
    }

    private static void appendNpcs(StringBuilder sb, List<Map<String, Object>> npcs) {
        sb.append('[');
        boolean first = true;
        for (Map<String, Object> n : npcs) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            String id = str(n.get("id"));
            String name = str(n.containsKey("displayName") ? n.get("displayName") : n.get("name"));
            if (name.isBlank()) {
                name = id;
            }
            sb.append('{')
                    .append("\"id\":").append(q(id)).append(',')
                    .append("\"name\":").append(q(name)).append(',')
                    .append("\"world\":").append(q(str(n.get("world")))).append(',')
                    .append("\"x\":").append(num(n.get("x"))).append(',')
                    .append("\"y\":").append(num(n.get("y"))).append(',')
                    .append("\"z\":").append(num(n.get("z")))
                    .append('}');
        }
        sb.append(']');
    }

    private static void appendRegions(StringBuilder sb, List<Map<String, Object>> regions) {
        sb.append('[');
        boolean first = true;
        for (Map<String, Object> r : regions) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('{')
                    .append("\"name\":").append(q(str(r.get("name")))).append(',')
                    .append("\"world\":").append(q(str(r.get("world")))).append(',')
                    .append("\"minX\":").append(inum(r.get("minX"))).append(',')
                    .append("\"minZ\":").append(inum(r.get("minZ"))).append(',')
                    .append("\"maxX\":").append(inum(r.get("maxX"))).append(',')
                    .append("\"maxZ\":").append(inum(r.get("maxZ")))
                    .append('}');
        }
        sb.append(']');
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static double num(Object o) {
        if (o instanceof Number n) {
            return Math.round(n.doubleValue() * 10.0) / 10.0;
        }
        try {
            return Math.round(Double.parseDouble(String.valueOf(o)) * 10.0) / 10.0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int inum(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return (int) Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String q(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", "") + "\"";
    }
}

package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardHoloUtil;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard CRUD for YaPHolo packet holograms. */
public final class DashboardHoloApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardHoloApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiHolo(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.holo(root));
            List<Map<String, Object>> holos = liveOrYaml(root);
            snap.put("ok", true);
            snap.put("holograms", holos);
            snap.put("holoCount", holos.size());
            snap.put("hint", "POST create | delete | move | setlines | attach | click | see | view | reload | list");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            String cmd = holoCommand(action, body);
            if (cmd == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "unknown action or missing fields"));
                return;
            }
            String result = server.executeCommand(cmd);
            if ("create".equals(action) && !body.getOrDefault("lines", "").isBlank()) {
                String set = holoCommand("setlines", body);
                if (set != null) {
                    String extra = server.executeCommand(set);
                    result = (result == null ? "" : result) + "\n" + (extra == null ? "" : extra);
                }
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("command", cmd);
            resp.put("result", result == null ? "" : result);
            if (!"info".equals(action)) {
                resp.put("holograms", liveOrYaml(root));
            }
            DashboardHttp.json(ex, 200, resp);
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private List<Map<String, Object>> liveOrYaml(Path root) {
        List<Map<String, Object>> live = DashboardHoloUtil.parseListJson(
                server.executeCommand("yapholo list json"));
        if (!live.isEmpty()) {
            return live;
        }
        return DashboardHoloUtil.fromYaml(root);
    }

    static String holoCommand(String action, Map<String, String> body) {
        return switch (action) {
            case "list" -> "yapholo list json";
            case "reload" -> "yapholo reload";
            case "delete", "remove" -> {
                String id = id(body);
                yield id == null ? null : "yapholo delete " + id;
            }
            case "info" -> {
                String id = id(body);
                yield id == null ? null : "yapholo info " + id;
            }
            case "view" -> {
                String id = id(body);
                String blocks = body.getOrDefault("view", body.getOrDefault("blocks", "")).trim();
                yield id == null || blocks.isEmpty() ? null : "yapholo view " + id + " " + blocks;
            }
            case "setlines" -> {
                String id = id(body);
                String lines = encodeLines(body.getOrDefault("lines", ""));
                yield id == null || lines.isBlank() ? null : "yapholo setlines " + id + " " + lines;
            }
            case "attach" -> {
                String id = id(body);
                String attach = body.getOrDefault("attach", "").trim();
                if (attach.isEmpty()) {
                    attach = "none";
                }
                yield id == null ? null : "yapholo attach " + id + " " + attach;
            }
            case "click", "clicks" -> {
                String id = id(body);
                String clicks = body.getOrDefault("clicks", "clear").trim();
                yield id == null ? null : "yapholo click " + id + " " + (clicks.isEmpty() ? "clear" : clicks);
            }
            case "see", "permission" -> {
                String id = id(body);
                String perm = body.getOrDefault("perm", body.getOrDefault("see", "none")).trim();
                yield id == null ? null : "yapholo see " + id + " " + (perm.isEmpty() ? "none" : perm);
            }
            case "move", "tp" -> {
                String id = id(body);
                String world = body.getOrDefault("world", "").trim();
                String x = body.getOrDefault("x", "").trim();
                String y = body.getOrDefault("y", "").trim();
                String z = body.getOrDefault("z", "").trim();
                if (id == null || world.isEmpty() || x.isEmpty() || y.isEmpty() || z.isEmpty()) {
                    yield null;
                }
                yield "yapholo move " + id + " at " + world + " " + x + " " + y + " " + z;
            }
            case "create" -> {
                String id = id(body);
                String world = body.getOrDefault("world", "world").trim();
                String x = body.getOrDefault("x", "0").trim();
                String y = body.getOrDefault("y", "64").trim();
                String z = body.getOrDefault("z", "0").trim();
                if (id == null || world.isEmpty()) {
                    yield null;
                }
                String text = encodeLines(body.getOrDefault("text", body.getOrDefault("lines", id)));
                if (text.contains("|") || text.contains(";;")) {
                    yield "yapholo create " + id + " at " + world + " " + x + " " + y + " " + z;
                }
                yield "yapholo create " + id + " at " + world + " " + x + " " + y + " " + z
                        + (text.isEmpty() ? "" : " " + text);
            }
            default -> null;
        };
    }

    static String encodeLines(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("\r", "");
        if (s.contains(";;") || (s.contains("|") && !s.contains("\n"))) {
            return s.trim();
        }
        String[] pages = s.split("\n\n+", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pages.length; i++) {
            if (i > 0) {
                out.append(";;");
            }
            out.append(pages[i].replace('\n', '|').trim());
        }
        return out.toString();
    }

    private static String id(Map<String, String> body) {
        String id = body.getOrDefault("id", "").trim();
        if (id.isEmpty() || !id.matches("[A-Za-z0-9_\\-]{1,32}")) {
            return null;
        }
        return id;
    }
}

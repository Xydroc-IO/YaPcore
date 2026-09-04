package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.DashboardNpcUtil;
import com.yapcore.web.DashboardRegionUtil;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard routes: regions and NPCs. */
public final class DashboardGameplayRegionsApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardGameplayRegionsApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiRegions(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.regions(root));
            List<Map<String, Object>> regions = DashboardRegionUtil.parseListJson(
                    server.executeCommand("region list json"));
            snap.put("ok", true);
            snap.put("regions", regions);
            snap.put("regionCount", regions.size());
            snap.put("hint", "POST define | redefine | remove | flag-set | list | reload");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            String cmd = regionCommand(action, body);
            if (cmd == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "unknown action or missing fields"));
                return;
            }
            String result = server.executeCommand(cmd);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("command", cmd);
            resp.put("result", result == null ? "" : result);
            resp.put("regions", DashboardRegionUtil.parseListJson(server.executeCommand("region list json")));
            DashboardHttp.json(ex, 200, resp);
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private static String regionCommand(String action, Map<String, String> body) {
        return switch (action) {
            case "list" -> "region list json";
            case "define", "redefine" -> {
                String name = body.getOrDefault("name", "").trim();
                String world = body.getOrDefault("world", "world").trim();
                if (name.isEmpty()) {
                    yield null;
                }
                yield "region " + action + " " + name + " at " + world + " "
                        + body.getOrDefault("x1", "0") + " "
                        + body.getOrDefault("y1", "0") + " "
                        + body.getOrDefault("z1", "0") + " "
                        + body.getOrDefault("x2", "0") + " "
                        + body.getOrDefault("y2", "255") + " "
                        + body.getOrDefault("z2", "0");
            }
            case "remove", "delete" -> {
                String name = body.getOrDefault("name", "").trim();
                if (name.isEmpty()) {
                    yield null;
                }
                yield "region remove " + name;
            }
            case "flag-set" -> {
                String name = body.getOrDefault("name", "").trim();
                String flag = body.getOrDefault("flag", "").trim();
                String value = body.getOrDefault("value", "allow").trim();
                if (name.isEmpty() || flag.isEmpty()) {
                    yield null;
                }
                yield "region flag set " + name + " " + flag + " " + value;
            }
            default -> null;
        };
    }

    public void apiNpcs(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.npcs(root));
            List<Map<String, Object>> npcs = DashboardNpcUtil.parseListJson(server.executeCommand("npc list json"));
            snap.put("ok", true);
            snap.put("npcs", npcs);
            snap.put("npcCount", npcs.size());
            snap.put("hint", "POST create | remove | setquest | setdialogue | respawn | reload | info");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            String cmd = npcCommand(action, body);
            if (cmd == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "unknown action or missing fields"));
                return;
            }
            String result = server.executeCommand(cmd);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("command", cmd);
            resp.put("result", result == null ? "" : result);
            if ("list".equals(action) || "create".equals(action) || "remove".equals(action)
                    || "setquest".equals(action) || "setdialogue".equals(action)) {
                resp.put("npcs", DashboardNpcUtil.parseListJson(server.executeCommand("npc list json")));
            }
            DashboardHttp.json(ex, 200, resp);
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private static String npcCommand(String action, Map<String, String> body) {
        return switch (action) {
            case "list" -> "npc list json";
            case "reload" -> "npc reload";
            case "respawn" -> "npc respawn";
            case "remove" -> {
                String id = body.getOrDefault("id", "").trim();
                yield id.isEmpty() ? null : "npc remove " + id;
            }
            case "info" -> {
                String id = body.getOrDefault("id", "").trim();
                yield id.isEmpty() ? null : "npc info " + id;
            }
            case "setquest" -> {
                String id = body.getOrDefault("id", "").trim();
                if (id.isEmpty()) {
                    yield null;
                }
                String quest = body.getOrDefault("questId", body.getOrDefault("quest", "")).trim();
                yield quest.isEmpty() ? "npc setquest " + id : "npc setquest " + id + " " + quest;
            }
            case "setdialogue" -> {
                String id = body.getOrDefault("id", "").trim();
                String dialogue = body.getOrDefault("dialogue", "").trim();
                yield id.isEmpty() || dialogue.isEmpty() ? null : "npc setdialogue " + id + " " + dialogue;
            }
            case "create" -> {
                String id = body.getOrDefault("id", "").trim();
                String world = body.getOrDefault("world", "world").trim();
                String x = body.getOrDefault("x", "0").trim();
                String y = body.getOrDefault("y", "64").trim();
                String z = body.getOrDefault("z", "0").trim();
                String yaw = body.getOrDefault("yaw", "0").trim();
                String name = body.getOrDefault("name", body.getOrDefault("displayName", id)).trim();
                if (id.isEmpty()) {
                    yield null;
                }
                StringBuilder sb = new StringBuilder("npc create ").append(id)
                        .append(" at ").append(world).append(' ')
                        .append(x).append(' ').append(y).append(' ').append(z)
                        .append(' ').append(yaw);
                if (!name.isEmpty() && !name.equals(id)) {
                    sb.append(' ').append(name);
                }
                yield sb.toString();
            }
            default -> null;
        };
    }
}

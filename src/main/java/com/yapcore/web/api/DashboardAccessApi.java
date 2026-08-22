package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.config.ServerConfig;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardNetworkSnapshotWriters;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Operators, default rank, and permission group management. */
public final class DashboardAccessApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardAccessApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiAccess(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        ServerConfig cfg = server.getConfig();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("ok", true);
            snap.put("ops", cfg.getOps());
            snap.put("autoOp", cfg.isAutoOp());
            snap.put("onlineMode", cfg.isOnlineMode());
            snap.putAll(DashboardNetworkSnapshots.perms(root));
            snap.put("hint", "POST save-ops | save-auto-op | set-default-group | op | deop | set-group | promote | demote | user-info | group-info | group-perm | user-perm");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            switch (action) {
                case "save-ops" -> {
                    List<String> ops = parseList(body.get("ops"));
                    cfg.setOps(ops);
                    cfg.save();
                    for (String name : ops) {
                        server.executeCommand("op " + name);
                    }
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "ops", ops));
                }
                case "save-auto-op" -> {
                    boolean auto = !"false".equalsIgnoreCase(body.getOrDefault("autoOp", "false"));
                    cfg.setAutoOp(auto);
                    cfg.save();
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "autoOp", auto));
                }
                case "set-default-group" -> {
                    String group = body.getOrDefault("group", "default").trim().toLowerCase();
                    DashboardNetworkSnapshotWriters.savePermsDefaultGroup(root, group);
                    server.executeCommand("yapperm reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "defaultGroup", group));
                }
                case "op" -> {
                    String p = body.getOrDefault("player", "").trim();
                    if (p.isEmpty()) {
                        DashboardHttp.json(ex, 400, Map.of("error", "player required"));
                        return;
                    }
                    List<String> ops = new ArrayList<>(cfg.getOps());
                    if (!ops.contains(p)) {
                        ops.add(p);
                        cfg.setOps(ops);
                        cfg.save();
                    }
                    String result = server.executeCommand("op " + p);
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result, "ops", ops));
                }
                case "deop" -> {
                    String p = body.getOrDefault("player", "").trim();
                    if (p.isEmpty()) {
                        DashboardHttp.json(ex, 400, Map.of("error", "player required"));
                        return;
                    }
                    List<String> ops = new ArrayList<>(cfg.getOps());
                    ops.removeIf(n -> n.equalsIgnoreCase(p));
                    cfg.setOps(ops);
                    cfg.save();
                    String result = server.executeCommand("deop " + p);
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result, "ops", ops));
                }
                default -> {
                    String cmd = permsCommand(action, body);
                    if (cmd == null) {
                        DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                        return;
                    }
                    String result = server.executeCommand(cmd);
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "command", cmd, "result", result == null ? "" : result));
                }
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private static String permsCommand(String action, Map<String, String> body) {
        String player = body.getOrDefault("player", "").trim();
        return switch (action) {
            case "reload" -> "yapperm reload";
            case "applypack" -> "yapperm applypack";
            case "user-info" -> player.isEmpty() ? null : "yapperm user " + player + " info";
            case "set-group" -> {
                String g = body.getOrDefault("group", "default");
                yield player.isEmpty() ? null : "yapperm user " + player + " parent set " + g;
            }
            case "add-group" -> {
                String g = body.getOrDefault("group", "");
                yield player.isEmpty() || g.isBlank() ? null : "yapperm user " + player + " parent add " + g;
            }
            case "remove-group" -> {
                String g = body.getOrDefault("group", "");
                yield player.isEmpty() || g.isBlank() ? null : "yapperm user " + player + " parent remove " + g;
            }
            case "promote" -> player.isEmpty() ? null : "promote " + player;
            case "demote" -> player.isEmpty() ? null : "demote " + player;
            case "group-info" -> {
                String g = body.getOrDefault("group", player);
                yield g.isBlank() ? null : "yapperm group info " + g;
            }
            case "group-list" -> "yapperm group list";
            case "group-perm" -> {
                String g = body.getOrDefault("group", "");
                String node = body.getOrDefault("node", "");
                String val = body.getOrDefault("value", "true");
                yield g.isBlank() || node.isBlank() ? null
                        : "yapperm group permission set " + g + " " + node + " " + val;
            }
            case "user-perm" -> {
                String node = body.getOrDefault("node", "");
                String val = body.getOrDefault("value", "true");
                yield player.isEmpty() || node.isBlank() ? null
                        : "yapperm user " + player + " permission set " + node + " " + val;
            }
            default -> null;
        };
    }

    private static List<String> parseList(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split("[,\\n]")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}

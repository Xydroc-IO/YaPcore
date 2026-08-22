package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardNetworkSnapshotWriters;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.DashboardProtectLookup;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dashboard routes: protect, world, chat, moderation, perms, playerdata. */
public final class DashboardGameplayOpsApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardGameplayOpsApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiProtect(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.protect(root));
            String status = server.executeCommand("yapprotect status");
            snap.put("ok", true);
            snap.put("status", status == null ? "" : status);
            snap.put("hint", "POST reload | prune | lookup | rollback");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "status").toLowerCase();
            String cmd = switch (action) {
                case "reload" -> "yapprotect reload";
                case "prune" -> "yapprotect prune " + body.getOrDefault("days", "30");
                case "lookup" -> "yapprotect dash-lookup user " + body.getOrDefault("player", "Steve") + " "
                        + body.getOrDefault("limit", "10");
                case "rollback" -> "yapprotect rollback " + body.getOrDefault("id", "0");
                default -> "yapprotect status";
            };
            String result = server.executeCommand(cmd);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("command", cmd);
            resp.put("result", result == null ? "" : result);
            if ("lookup".equals(action)) {
                resp.put("lookupRows", DashboardProtectLookup.parseDashJson(result));
            }
            DashboardHttp.json(ex, 200, resp);
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiWorld(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.world(root));
            String status = server.executeCommand("yapworld status");
            snap.put("ok", true);
            snap.put("status", status == null ? "" : status);
            snap.put("hint", "POST reload | load | unload | pregen-status");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "status").toLowerCase();
            String world = body.getOrDefault("world", "world");
            String cmd = switch (action) {
                case "reload" -> "yapworld reload";
                case "load" -> "yapworld load " + world;
                case "unload" -> "yapworld unload " + world;
                case "pregen-status" -> "yapworld pregen status";
                default -> "yapworld status";
            };
            String result = server.executeCommand(cmd);
            DashboardHttp.json(ex, 200, Map.of("ok", true, "command", cmd, "result", result == null ? "" : result));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiChat(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.chat(root));
            snap.put("ok", true);
            snap.put("hint", "POST reload | clearchat | set-default-channel");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            String cmd = switch (action) {
                case "reload" -> "yapchat reload";
                case "clearchat" -> "clearchat";
                default -> null;
            };
            if (cmd == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                return;
            }
            String result = server.executeCommand(cmd);
            DashboardHttp.json(ex, 200, Map.of("ok", true, "command", cmd, "result", result == null ? "" : result));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiModeration(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.moderation(root));
            snap.put("ok", true);
            snap.put("hint", "POST reload | history | unban");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            String cmd = switch (action) {
                case "reload" -> "yapmod reload";
                case "history" -> "history " + body.getOrDefault("player", "Steve");
                case "unban" -> "unban " + body.getOrDefault("player", "Steve");
                default -> null;
            };
            if (cmd == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                return;
            }
            String result = server.executeCommand(cmd);
            DashboardHttp.json(ex, 200, Map.of("ok", true, "command", cmd, "result", result == null ? "" : result));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiPerms(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.perms(root));
            snap.put("ok", true);
            snap.put("hint", "POST reload | applypack (see Ranks tab for rank pack)");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            String cmd = switch (action) {
                case "reload" -> "yapperm reload";
                case "applypack" -> "yapperm applypack";
                default -> null;
            };
            if (cmd == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                return;
            }
            String result = server.executeCommand(cmd);
            DashboardHttp.json(ex, 200, Map.of("ok", true, "command", cmd, "result", result == null ? "" : result));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiPlayerdata(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.playerdata(root));
            String status = server.executeCommand("yapdata status");
            snap.put("ok", true);
            snap.put("status", status == null ? "" : status);
            snap.put("hint", "POST reload | save | set-feature");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            switch (action) {
                case "reload" -> {
                    String result = server.executeCommand("yapdata reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "command", "yapdata reload",
                            "result", result == null ? "" : result));
                }
                case "save" -> {
                    String result = server.executeCommand("yapdata save");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "command", "yapdata save",
                            "result", result == null ? "" : result));
                }
                case "set-feature" -> {
                    String feature = body.getOrDefault("feature", "").trim();
                    if (feature.isEmpty()) {
                        DashboardHttp.json(ex, 400, Map.of("error", "feature required"));
                        return;
                    }
                    boolean enabled = !"false".equalsIgnoreCase(body.getOrDefault("enabled", "true"));
                    DashboardNetworkSnapshotWriters.savePlayerdataFeature(root, feature, enabled);
                    server.executeCommand("yapdata reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "feature", feature, "enabled", enabled));
                }
                default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }
}

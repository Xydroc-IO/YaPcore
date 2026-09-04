package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardCommands;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Web CRUD for {@code plugins/YaPCommands/commands.yml}. */
public final class DashboardCommandsApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardCommandsApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiCommands(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardCommands.snapshot(root));
            snap.put("ok", true);
            snap.put("hint", "POST save-command | delete-command | clone-command | set-require-use | reload");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            switch (action) {
                case "save-command" -> handleSave(ex, root, body);
                case "delete-command" -> handleDelete(ex, root, body);
                case "clone-command" -> handleClone(ex, root, body);
                case "set-require-use" -> handleRequireUse(ex, root, body);
                case "reload" -> {
                    String result = live("yapcommands reload");
                    Map<String, Object> resp = new LinkedHashMap<>(DashboardCommands.snapshot(root));
                    resp.put("ok", true);
                    resp.put("result", result);
                    DashboardHttp.json(ex, 200, resp);
                }
                default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private void handleSave(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        String id = DashboardCommands.normalizeId(body.getOrDefault("name", body.getOrDefault("id", "")));
        if (id.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "command name required"));
            return;
        }
        if (DashboardCommands.isReserved(id)) {
            DashboardHttp.json(ex, 400, Map.of("error", "reserved command name"));
            return;
        }
        try {
            Map<String, Object> cmd = new LinkedHashMap<>();
            cmd.put("name", id);
            cmd.put("enabled", Boolean.parseBoolean(body.getOrDefault("enabled", "true")));
            cmd.put("aliases", splitLines(body.get("aliases")));
            cmd.put("permission", body.getOrDefault("permission", ""));
            cmd.put("description", body.getOrDefault("description", ""));
            cmd.put("cooldownSeconds", parseInt(body.get("cooldownSeconds"), 0));
            cmd.put("hideNoPermission", Boolean.parseBoolean(body.getOrDefault("hideNoPermission", "true")));
            cmd.put("messages", splitLines(body.get("messages")));
            cmd.put("playerCommands", splitLines(body.get("playerCommands")));
            cmd.put("consoleCommands", splitLines(body.get("consoleCommands")));
            cmd.put("broadcast", body.getOrDefault("broadcast", ""));
            DashboardCommands.saveCommand(root, cmd);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("command", id);
            resp.put("reload", live("yapcommands reload"));
            resp.put("commands", DashboardCommands.listCommands(root));
            DashboardHttp.json(ex, 200, resp);
        } catch (IllegalArgumentException e) {
            DashboardHttp.json(ex, 400, Map.of("error", e.getMessage()));
        }
    }

    private void handleDelete(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        String id = DashboardCommands.normalizeId(body.getOrDefault("name", body.getOrDefault("id", "")));
        if (id.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "command name required"));
            return;
        }
        DashboardCommands.deleteCommand(root, id);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("reload", live("yapcommands reload"));
        resp.put("commands", DashboardCommands.listCommands(root));
        DashboardHttp.json(ex, 200, resp);
    }

    private void handleClone(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        try {
            DashboardCommands.cloneCommand(root, body.get("from"), body.get("to"));
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("reload", live("yapcommands reload"));
            resp.put("commands", DashboardCommands.listCommands(root));
            DashboardHttp.json(ex, 200, resp);
        } catch (IllegalArgumentException e) {
            DashboardHttp.json(ex, 400, Map.of("error", e.getMessage()));
        }
    }

    private void handleRequireUse(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        boolean value = Boolean.parseBoolean(body.getOrDefault("requireUsePerm", "true"));
        DashboardCommands.setRequireUsePerm(root, value);
        Map<String, Object> resp = new LinkedHashMap<>(DashboardCommands.snapshot(root));
        resp.put("ok", true);
        resp.put("reload", live("yapcommands reload"));
        DashboardHttp.json(ex, 200, resp);
    }

    private String live(String cmd) {
        try {
            return server.executeCommand(cmd);
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : "failed";
        }
    }

    private static int parseInt(String raw, int def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static List<String> splitLines(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.replace(',', '\n').split("\\R")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}

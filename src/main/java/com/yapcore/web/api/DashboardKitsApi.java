package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardKits;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Web kit builder for {@code plugins/YaPPlayerData/kits.yml}. */
public final class DashboardKitsApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardKitsApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiKits(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardKits.snapshot(root));
            snap.put("ok", true);
            snap.put("hint", "POST save-kit | delete-kit | clone-kit | give | grant | reload");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            switch (action) {
                case "save-kit" -> handleSave(ex, root, body);
                case "delete-kit" -> handleDelete(ex, root, body);
                case "clone-kit" -> handleClone(ex, root, body);
                case "give" -> handleGive(ex, body, false);
                case "grant" -> handleGive(ex, body, true);
                case "reload" -> {
                    String result = live("yapdata reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result));
                }
                default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private void handleSave(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        String id = DashboardKits.normalizeId(body.getOrDefault("id", body.getOrDefault("name", "")));
        if (id.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "kit id required"));
            return;
        }
        try {
            Map<String, Object> kit = new LinkedHashMap<>();
            kit.put("id", id);
            kit.put("delaySeconds", parseLong(body.get("delaySeconds"), 86400));
            kit.put("maxUses", parseInt(body.get("maxUses"), 0));
            kit.put("cost", parseDouble(body.get("cost"), 0));
            kit.put("firstJoin", Boolean.parseBoolean(body.getOrDefault("firstJoin", "false")));
            kit.put("commands", splitLines(body.get("commands")));
            kit.put("items", DashboardKits.decodeItems(body.get("items")));
            DashboardKits.saveKit(root, kit);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("kit", id);
            resp.put("reload", live("yapdata reload"));
            resp.put("kits", DashboardKits.listKits(root));
            DashboardHttp.json(ex, 200, resp);
        } catch (Exception e) {
            DashboardHttp.json(ex, 500, Map.of("error", e.getMessage() == null ? "save failed" : e.getMessage()));
        }
    }

    private void handleDelete(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        String id = DashboardKits.normalizeId(body.getOrDefault("id", body.getOrDefault("name", "")));
        if (id.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "kit id required"));
            return;
        }
        try {
            DashboardKits.deleteKit(root, id);
            DashboardHttp.json(ex, 200, Map.of(
                    "ok", true,
                    "kit", id,
                    "reload", live("yapdata reload"),
                    "kits", DashboardKits.listKits(root)));
        } catch (Exception e) {
            DashboardHttp.json(ex, 500, Map.of("error", e.getMessage() == null ? "delete failed" : e.getMessage()));
        }
    }

    private void handleClone(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        String from = DashboardKits.normalizeId(body.getOrDefault("from", body.getOrDefault("id", "")));
        String to = DashboardKits.normalizeId(body.getOrDefault("to", body.getOrDefault("name", "")));
        if (from.isEmpty() || to.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "from and to required"));
            return;
        }
        try {
            DashboardKits.cloneKit(root, from, to);
            DashboardHttp.json(ex, 200, Map.of(
                    "ok", true,
                    "from", from,
                    "kit", to,
                    "reload", live("yapdata reload"),
                    "kits", DashboardKits.listKits(root)));
        } catch (Exception e) {
            DashboardHttp.json(ex, 500, Map.of("error", e.getMessage() == null ? "clone failed" : e.getMessage()));
        }
    }

    private void handleGive(HttpExchange ex, Map<String, String> body, boolean grant) throws IOException {
        String player = body.getOrDefault("player", "").trim();
        String kit = DashboardKits.normalizeId(body.get("kit"));
        if (player.isEmpty() || kit.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "player and kit required"));
            return;
        }
        String cmd = (grant ? "kit grant " : "kit give ") + player + " " + kit;
        DashboardHttp.json(ex, 200, Map.of("ok", true, "command", cmd, "result", live(cmd)));
    }

    private String live(String command) {
        try {
            String result = server.executeCommand(command);
            return result == null ? "" : result;
        } catch (Exception e) {
            return e.getMessage() == null ? "game server not running" : e.getMessage();
        }
    }

    private static List<String> splitLines(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split("\n")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardFactionsSnapshot;
import com.yapcore.web.DashboardSkillsSnapshot;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dashboard routes: skills, factions. */
public final class DashboardGameplayModesApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardGameplayModesApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiSkills(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            handleSkillsPost(ex);
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        Path root = server.getRootDir();
        Map<String, Object> snap = new LinkedHashMap<>(DashboardSkillsSnapshot.snapshot(root));
        snap.put("onlinePlayers", server.getOnlinePlayers());
        if (server.isRunning() && server.getBukkitServer() != null) {
            List<Map<String, Object>> sample = new ArrayList<>();
            var bukkit = server.getBukkitServer();
            for (org.bukkit.entity.Player player : bukkit.getOnlinePlayers()) {
                if (sample.size() >= 5) {
                    break;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", player.getName());
                row.put("uuid", player.getUniqueId().toString());
                sample.add(row);
            }
            DashboardSkillsSnapshot.enrichOnlineSample(snap, sample);
        }
        DashboardHttp.json(ex, 200, snap);
    }

    private void handleSkillsPost(HttpExchange ex) throws IOException {
        Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
        String action = body.getOrDefault("action", "");
        if (!server.isRunning()) {
            DashboardHttp.json(ex, 400, Map.of("error", "server not running"));
            return;
        }
        switch (action) {
            case "reload", "reload-skills" -> {
                String out = server.executeCommand("yskills reload");
                DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action, "result", out == null ? "" : out));
            }
            default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
        }
    }

    public void apiFactions(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardFactionsSnapshot.snapshot(root));
            snap.put("onlinePlayers", server.getOnlinePlayers());
            snap.put("ok", true);
            snap.put("hint", "POST reload | save-settings | setpower | setjoin | disband");
            if (server.isRunning()) {
                String live = server.executeCommand("yapfactions snapshot json");
                if (live != null && live.contains("YAPFACTIONS_JSON:")) {
                    int idx = live.indexOf("YAPFACTIONS_JSON:");
                    snap.put("live", live.substring(idx + "YAPFACTIONS_JSON:".length()).trim());
                }
            }
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase(Locale.ROOT);
            try {
                switch (action) {
                    case "save-settings" -> {
                        DashboardFactionsSnapshot.saveSettings(root, body);
                        String reload = server.isRunning() ? server.executeCommand("yapfactions reload") : "";
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "reload", reload == null ? "" : reload));
                    }
                    case "reload" -> {
                        if (!server.isRunning()) {
                            DashboardHttp.json(ex, 400, Map.of("error", "server not running"));
                            return;
                        }
                        String result = server.executeCommand("yapfactions reload");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "result", result == null ? "" : result));
                    }
                    case "setpower" -> {
                        if (!server.isRunning()) {
                            DashboardHttp.json(ex, 400, Map.of("error", "server not running"));
                            return;
                        }
                        String faction = body.getOrDefault("faction", "");
                        String power = body.getOrDefault("power", "0");
                        String max = body.getOrDefault("max", "");
                        String cmd = "yapfactions setpower " + faction + " " + power
                                + (max.isBlank() ? "" : " " + max);
                        String result = server.executeCommand(cmd);
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "command", cmd,
                                "result", result == null ? "" : result));
                    }
                    case "setjoin" -> {
                        if (!server.isRunning()) {
                            DashboardHttp.json(ex, 400, Map.of("error", "server not running"));
                            return;
                        }
                        String faction = body.getOrDefault("faction", "");
                        String mode = body.getOrDefault("mode", "invite");
                        String cmd = "yapfactions setjoin " + faction + " " + mode;
                        String result = server.executeCommand(cmd);
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "command", cmd,
                                "result", result == null ? "" : result));
                    }
                    case "disband" -> {
                        if (!server.isRunning()) {
                            DashboardHttp.json(ex, 400, Map.of("error", "server not running"));
                            return;
                        }
                        String faction = body.getOrDefault("faction", "");
                        String cmd = "yapfactions disband " + faction;
                        String result = server.executeCommand(cmd);
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "command", cmd,
                                "result", result == null ? "" : result));
                    }
                    default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                }
            } catch (Exception e) {
                DashboardHttp.json(ex, 500, Map.of(
                        "error", e.getMessage() == null ? "factions action failed" : e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }
}

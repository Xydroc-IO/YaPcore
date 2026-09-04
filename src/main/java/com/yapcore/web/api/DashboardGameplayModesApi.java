package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardAbilitiesSnapshot;
import com.yapcore.web.DashboardFactionsSnapshot;
import com.yapcore.web.DashboardGuildsSnapshot;
import com.yapcore.web.DashboardGamesSnapshot;
import com.yapcore.web.DashboardMmoSnapshot;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dashboard routes: mmo, factions, guilds, games. */
public final class DashboardGameplayModesApi {

    private static final Gson GSON = new Gson();

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardGameplayModesApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiMmo(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            handleMmoPost(ex);
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        Path root = server.getRootDir();
        Map<String, Object> snap = new LinkedHashMap<>(DashboardMmoSnapshot.snapshot(root));
        snap.put("abilities", new LinkedHashMap<>(DashboardAbilitiesSnapshot.snapshot(root)));
        snap.put("onlinePlayers", server.getOnlinePlayers());
        if (server.isRunning()) {
            String live = server.executeCommand("yapmmo snapshot json");
            if (live != null && live.contains("YAPMMO_JSON:")) {
                int idx = live.indexOf("YAPMMO_JSON:");
                String json = live.substring(idx + "YAPMMO_JSON:".length()).trim();
                Map<String, String> liveSnap = TinyJson.parseFlatObject(json);
                if (!liveSnap.isEmpty()) {
                    snap.put("live", new LinkedHashMap<>(liveSnap));
                    if (liveSnap.containsKey("bossKills")) {
                        snap.put("bossKillTotals", liveSnap.get("bossKills"));
                    }
                    if (liveSnap.containsKey("hiscorePreview")) {
                        snap.put("hiscorePreview", liveSnap.get("hiscorePreview"));
                    }
                }
            }
            mergeAbilitiesLive(snap, server.executeCommand("yapabilities snapshot json"));
        }
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
            DashboardMmoSnapshot.enrichOnlineSample(snap, sample);
        }
        DashboardHttp.json(ex, 200, snap);
    }

    private void handleMmoPost(HttpExchange ex) throws IOException {
        Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
        String action = body.getOrDefault("action", "");
        if (!server.isRunning()) {
            DashboardHttp.json(ex, 400, Map.of("error", "server not running"));
            return;
        }
        switch (action) {
            case "reload-abilities" -> {
                String out = server.executeCommand("yapabilities reload");
                DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action, "result", out == null ? "" : out));
            }
            case "reload-mmo" -> {
                String out = server.executeCommand("yapmmo reload");
                DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action, "result", out == null ? "" : out));
            }
            default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void mergeAbilitiesLive(Map<String, Object> snap, String live) {
        if (live == null || !live.contains("YAPABILITIES_JSON:")) {
            return;
        }
        int idx = live.indexOf("YAPABILITIES_JSON:");
        String json = live.substring(idx + "YAPABILITIES_JSON:".length()).trim();
        if (json.isBlank()) {
            return;
        }
        try {
            Map<String, Object> abilities = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            if (abilities != null && !abilities.isEmpty()) {
                snap.put("abilities", abilities);
                snap.put("abilitiesLive", true);
            }
        } catch (Exception ignored) {
            // keep filesystem snapshot
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

    public void apiGuilds(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardGuildsSnapshot.snapshot(root));
            snap.put("onlinePlayers", server.getOnlinePlayers());
            snap.put("ok", true);
            snap.put("hint", "POST reload | save-settings | setlevel | disband");
            if (server.isRunning()) {
                String live = server.executeCommand("yapguilds snapshot json");
                if (live != null && live.contains("YAPGUILDS_JSON:")) {
                    int idx = live.indexOf("YAPGUILDS_JSON:");
                    snap.put("live", live.substring(idx + "YAPGUILDS_JSON:".length()).trim());
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
                        DashboardGuildsSnapshot.saveSettings(root, body);
                        String reload = server.isRunning() ? server.executeCommand("yapguilds reload") : "";
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "reload", reload == null ? "" : reload));
                    }
                    case "reload" -> {
                        if (!server.isRunning()) {
                            DashboardHttp.json(ex, 400, Map.of("error", "server not running"));
                            return;
                        }
                        String result = server.executeCommand("yapguilds reload");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "result", result == null ? "" : result));
                    }
                    case "setlevel" -> {
                        if (!server.isRunning()) {
                            DashboardHttp.json(ex, 400, Map.of("error", "server not running"));
                            return;
                        }
                        String guild = body.getOrDefault("guild", "");
                        String level = body.getOrDefault("level", "1");
                        String xp = body.getOrDefault("xp", "");
                        String cmd = "yapguilds setlevel " + guild + " " + level
                                + (xp.isBlank() ? "" : " " + xp);
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
                        String guild = body.getOrDefault("guild", "");
                        String cmd = "yapguilds disband " + guild;
                        String result = server.executeCommand(cmd);
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "command", cmd,
                                "result", result == null ? "" : result));
                    }
                    default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                }
            } catch (Exception e) {
                DashboardHttp.json(ex, 500, Map.of(
                        "error", e.getMessage() == null ? "guilds action failed" : e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiGames(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardGamesSnapshot.snapshot(root));
            snap.put("onlinePlayers", server.getOnlinePlayers());
            snap.put("ok", true);
            snap.put("hint", "POST reload | save-settings | list | forcestart");
            if (server.isRunning()) {
                String live = server.executeCommand("ygames snapshot json");
                if (live != null && live.contains("YAPGAMES_JSON:")) {
                    int idx = live.indexOf("YAPGAMES_JSON:");
                    snap.put("live", live.substring(idx + "YAPGAMES_JSON:".length()).trim());
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
                        DashboardGamesSnapshot.saveSettings(root, body);
                        String reload = server.isRunning() ? server.executeCommand("ygames reload") : "";
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "reload", reload == null ? "" : reload));
                    }
                    case "reload" -> {
                        if (!server.isRunning()) {
                            DashboardHttp.json(ex, 400, Map.of("error", "server not running"));
                            return;
                        }
                        String result = server.executeCommand("ygames reload");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "result", result == null ? "" : result));
                    }
                    case "list" -> {
                        if (!server.isRunning()) {
                            DashboardHttp.json(ex, 400, Map.of("error", "server not running"));
                            return;
                        }
                        String result = server.executeCommand("ygames list");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "result", result == null ? "" : result));
                    }
                    case "forcestart" -> {
                        if (!server.isRunning()) {
                            DashboardHttp.json(ex, 400, Map.of("error", "server not running"));
                            return;
                        }
                        String mode = body.getOrDefault("mode", "");
                        String cmd = "ygames forcestart " + mode;
                        String result = server.executeCommand(cmd);
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "command", cmd,
                                "result", result == null ? "" : result));
                    }
                    default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                }
            } catch (Exception e) {
                DashboardHttp.json(ex, 500, Map.of(
                        "error", e.getMessage() == null ? "games action failed" : e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }
}

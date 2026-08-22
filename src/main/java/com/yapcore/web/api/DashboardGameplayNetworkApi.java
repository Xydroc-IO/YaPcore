package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.DashboardNetworkSnapshotWriters;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard routes: Discord, TAB, map, guard, regions, NPCs. */
public final class DashboardGameplayNetworkApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardGameplayNetworkApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiDiscord(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.discord(root));
            snap.put("ok", true);
            snap.put("hint", "POST save-webhook | save-relay | test-webhook | reload");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            switch (action) {
                case "save-webhook" -> {
                    DashboardNetworkSnapshotWriters.saveDiscordWebhook(root,
                            body.getOrDefault("key", "moderation"),
                            body.getOrDefault("url", ""));
                    server.executeCommand("yapdiscord reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                }
                case "save-relay" -> {
                    boolean mcToDiscord = !"false".equalsIgnoreCase(body.getOrDefault("mcToDiscord", "false"));
                    Boolean discordToMc = body.containsKey("discordToMc")
                            ? !"false".equalsIgnoreCase(body.get("discordToMc"))
                            : null;
                    DashboardNetworkSnapshotWriters.saveDiscordRelay(root, mcToDiscord, discordToMc);
                    server.executeCommand("yapdiscord reload");
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true, "action", action,
                            "mcToDiscord", mcToDiscord,
                            "discordToMc", discordToMc == null ? false : discordToMc));
                }
                case "test-webhook" -> {
                    String key = body.getOrDefault("key", "moderation").toLowerCase();
                    String cmd = "yapdiscord test " + ("chat".equals(key) ? "chat" : "moderation");
                    String result = server.executeCommand(cmd);
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true, "action", action, "key", key,
                            "result", result == null ? "" : result));
                }
                case "reload" -> {
                    String result = server.executeCommand("yapdiscord reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result));
                }
                default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiTab(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.tab(root));
            snap.put("ok", true);
            snap.put("hint", "POST save-header | save-footer | save-sidebar | save-settings | save-bossbar | reload");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            switch (action) {
                case "save-header" -> {
                    List<String> lines = DashboardApiUtil.splitLines(body.getOrDefault("text", ""));
                    DashboardNetworkSnapshotWriters.saveTabHeader(root, lines);
                    server.executeCommand("yaptab reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action, "lines", lines.size()));
                }
                case "save-footer" -> {
                    List<String> lines = DashboardApiUtil.splitLines(body.getOrDefault("text", ""));
                    DashboardNetworkSnapshotWriters.saveTabFooter(root, lines);
                    server.executeCommand("yaptab reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action, "lines", lines.size()));
                }
                case "save-sidebar" -> {
                    List<String> lines = DashboardApiUtil.splitLines(body.getOrDefault("text", ""));
                    Boolean enabled = body.containsKey("enabled")
                            ? !"false".equalsIgnoreCase(body.get("enabled"))
                            : null;
                    DashboardNetworkSnapshotWriters.saveTabSidebar(root, lines, enabled);
                    server.executeCommand("yaptab reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action, "lines", lines.size()));
                }
                case "save-settings" -> {
                    Boolean sidebar = body.containsKey("sidebarEnabled")
                            ? !"false".equalsIgnoreCase(body.get("sidebarEnabled")) : null;
                    Boolean nametag = body.containsKey("nametagTeams")
                            ? !"false".equalsIgnoreCase(body.get("nametagTeams")) : null;
                    Integer refresh = null;
                    if (body.containsKey("refreshSeconds")) {
                        try {
                            refresh = Integer.parseInt(body.get("refreshSeconds"));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    Boolean networkSync = body.containsKey("networkSyncEnabled")
                            ? !"false".equalsIgnoreCase(body.get("networkSyncEnabled")) : null;
                    DashboardNetworkSnapshotWriters.saveTabSettings(root, sidebar, nametag, refresh, networkSync);
                    server.executeCommand("yaptab reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                }
                case "save-bossbar" -> {
                    Boolean enabled = body.containsKey("enabled")
                            ? !"false".equalsIgnoreCase(body.get("enabled")) : null;
                    Boolean welcome = body.containsKey("welcomeOnJoin")
                            ? !"false".equalsIgnoreCase(body.get("welcomeOnJoin")) : null;
                    Integer duration = null;
                    if (body.containsKey("durationSeconds")) {
                        try {
                            duration = Integer.parseInt(body.get("durationSeconds"));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    DashboardNetworkSnapshotWriters.saveTabBossBar(
                            root, enabled, welcome,
                            body.get("title"), body.get("subtitle"),
                            body.get("color"), duration);
                    server.executeCommand("yaptab reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                }
                case "reload" -> {
                    String result = server.executeCommand("yaptab reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result));
                }
                default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiMap(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.map(root));
            snap.put("ok", true);
            snap.put("hint", "POST reload | render");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String cmd = switch (body.getOrDefault("action", "").toLowerCase()) {
                case "reload" -> "yapmap reload";
                case "render" -> "yapmap render";
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

    public void apiGuard(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.guard(root));
            String status = server.executeCommand("yapguard status");
            snap.put("ok", true);
            snap.put("status", status == null ? "" : status);
            snap.put("hint", "POST reload | alerts-on | alerts-off");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String cmd = switch (body.getOrDefault("action", "").toLowerCase()) {
                case "reload" -> "yapguard reload";
                case "alerts-on" -> "yapguard alerts on";
                case "alerts-off" -> "yapguard alerts off";
                case "player-status" -> "yapguard status " + body.getOrDefault("player", "");
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

    public void apiRegions(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.regions(root));
            String status = server.executeCommand("region list");
            snap.put("ok", true);
            snap.put("status", status == null ? "" : status);
            snap.put("regionLines", DashboardApiUtil.splitLines(status));
            snap.put("hint", "POST reload | list");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String cmd = switch (body.getOrDefault("action", "").toLowerCase()) {
                case "list" -> "region list";
                default -> null;
            };
            if (cmd == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                return;
            }
            String result = server.executeCommand(cmd);
            DashboardHttp.json(ex, 200, Map.of(
                    "ok", true,
                    "command", cmd,
                    "result", result == null ? "" : result,
                    "regionLines", DashboardApiUtil.splitLines(result)));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiNpcs(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.npcs(root));
            String npcList = server.executeCommand("npc list");
            snap.put("ok", true);
            snap.put("npcList", npcList == null ? "" : npcList);
            snap.put("hint", "POST reload | list");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String cmd = switch (body.getOrDefault("action", "").toLowerCase()) {
                case "list" -> "npc list";
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
}

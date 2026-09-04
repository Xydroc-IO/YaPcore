package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.DashboardNetworkSnapshotWriters;
import com.yapcore.web.DashboardNpcUtil;
import com.yapcore.web.DashboardRegionUtil;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard routes: Discord, Tebex, TAB, map, guard, regions, NPCs. */
public final class DashboardGameplayNetworkApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    private final DashboardGameplayRegionsApi regions;

    public DashboardGameplayNetworkApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
        this.regions = new DashboardGameplayRegionsApi(server, auth);
    }

    public void apiDiscord(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.discord(root));
            snap.put("ok", true);
            snap.put("hint", "POST save-webhook | save-relay | save-events | test-webhook | reload");
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
                case "save-events" -> {
                    Boolean join = body.containsKey("join")
                            ? !"false".equalsIgnoreCase(body.get("join")) : null;
                    Boolean leave = body.containsKey("leave")
                            ? !"false".equalsIgnoreCase(body.get("leave")) : null;
                    Boolean death = body.containsKey("death")
                            ? !"false".equalsIgnoreCase(body.get("death")) : null;
                    Boolean advancement = body.containsKey("advancement")
                            ? !"false".equalsIgnoreCase(body.get("advancement")) : null;
                    DashboardNetworkSnapshotWriters.saveDiscordEvents(root, join, leave, death, advancement);
                    server.executeCommand("yapdiscord reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                }
                case "test-webhook" -> {
                    String key = body.getOrDefault("key", "moderation").toLowerCase();
                    String target = switch (key) {
                        case "chat" -> "chat";
                        case "events", "event" -> "events";
                        default -> "moderation";
                    };
                    String result = server.executeCommand("yapdiscord test " + target);
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true, "action", action, "key", target,
                            "result", result == null ? "" : result));
                }
                case "reload" -> {
                    String result = server.executeCommand("yapdiscord reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result));
                }
                case "save-inbound" -> {
                    Boolean enabled = body.containsKey("enabled")
                            ? !"false".equalsIgnoreCase(body.get("enabled")) : null;
                    Integer port = null;
                    if (body.containsKey("port")) {
                        port = Integer.parseInt(body.get("port"));
                    }
                    String secret = body.get("secret");
                    DashboardNetworkSnapshotWriters.saveDiscordInbound(root, enabled, port, secret);
                    server.executeCommand("yapdiscord reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                }
                default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiTebex(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.tebex(root));
            snap.put("ok", true);
            snap.put("hint", "POST set-secret | save-settings | reload | info | forcecheck");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            switch (action) {
                case "set-secret", "secret" -> {
                    String secret = body.getOrDefault("secret", body.getOrDefault("key", "")).trim();
                    if (secret.isEmpty()) {
                        DashboardHttp.json(ex, 400, Map.of("error", "secret required"));
                        return;
                    }
                    if (secret.contains(" ") || secret.contains("\"") || secret.contains("'")
                            || secret.contains("\n") || secret.contains("\r")) {
                        DashboardHttp.json(ex, 400, Map.of("error", "secret must be a single token (no spaces/quotes)"));
                        return;
                    }
                    DashboardNetworkSnapshotWriters.saveTebexSecret(root, secret);
                    String result = server.executeCommand("tebex secret " + secret);
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true,
                            "action", "set-secret",
                            "secretConfigured", true,
                            "secretMasked", DashboardNetworkSnapshots.maskSecret(secret),
                            "result", result == null ? "" : result));
                }
                case "save-settings" -> {
                    Boolean buyEnabled = body.containsKey("buyCommandEnabled")
                            ? !"false".equalsIgnoreCase(body.get("buyCommandEnabled")) : null;
                    Boolean proxy = body.containsKey("proxyMode")
                            ? !"false".equalsIgnoreCase(body.get("proxyMode")) : null;
                    Boolean verbose = body.containsKey("verbose")
                            ? !"false".equalsIgnoreCase(body.get("verbose")) : null;
                    String buyName = body.get("buyCommandName");
                    DashboardNetworkSnapshotWriters.saveTebexSettings(root, buyEnabled, buyName, proxy, verbose);
                    String result = server.executeCommand("tebex reload");
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true,
                            "action", action,
                            "result", result == null ? "" : result));
                }
                case "reload" -> {
                    String result = server.executeCommand("tebex reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result));
                }
                case "info" -> {
                    String result = server.executeCommand("tebex info");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result));
                }
                case "forcecheck", "force-check" -> {
                    String result = server.executeCommand("tebex forcecheck");
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
            String action = body.getOrDefault("action", "").toLowerCase();
            if ("save-settings".equals(action)) {
                try {
                    Integer interval = body.containsKey("renderIntervalMinutes")
                            ? Integer.parseInt(body.get("renderIntervalMinutes")) : null;
                    List<String> worlds = null;
                    if (body.containsKey("worlds")) {
                        worlds = DashboardApiUtil.splitLines(body.get("worlds").replace(",", "\n"));
                    }
                    Boolean markersPlayers = body.containsKey("markersPlayers")
                            ? !"false".equalsIgnoreCase(body.get("markersPlayers")) : null;
                    Boolean markersNpcs = body.containsKey("markersNpcs")
                            ? !"false".equalsIgnoreCase(body.get("markersNpcs")) : null;
                    Boolean markersRegions = body.containsKey("markersRegions")
                            ? !"false".equalsIgnoreCase(body.get("markersRegions")) : null;
                    Integer markersPoll = null;
                    if (body.containsKey("markersPollSeconds")) {
                        markersPoll = Integer.parseInt(body.get("markersPollSeconds"));
                    }
                    DashboardNetworkSnapshotWriters.saveMapSettings(root, interval, worlds,
                            markersPlayers, markersNpcs, markersRegions, markersPoll);
                    server.executeCommand("yapmap reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                } catch (Exception e) {
                    DashboardHttp.json(ex, 500, Map.of("error", e.getMessage()));
                }
                return;
            }
            String cmd = switch (action) {
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
            String action = body.getOrDefault("action", "").toLowerCase();
            if ("save-settings".equals(action)) {
                try {
                    DashboardNetworkSnapshotWriters.saveGuardSettings(root,
                            body.containsKey("flyEnabled") ? !"false".equalsIgnoreCase(body.get("flyEnabled")) : null,
                            body.containsKey("speedEnabled") ? !"false".equalsIgnoreCase(body.get("speedEnabled")) : null,
                            body.containsKey("reachEnabled") ? !"false".equalsIgnoreCase(body.get("reachEnabled")) : null,
                            body.containsKey("scaffoldEnabled") ? !"false".equalsIgnoreCase(body.get("scaffoldEnabled")) : null,
                            body.containsKey("maxViolations") ? Integer.parseInt(body.get("maxViolations")) : null,
                            body.containsKey("decaySeconds") ? Integer.parseInt(body.get("decaySeconds")) : null,
                            body.containsKey("alertsEnabled") ? !"false".equalsIgnoreCase(body.get("alertsEnabled")) : null);
                    server.executeCommand("yapguard reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                } catch (Exception e) {
                    DashboardHttp.json(ex, 500, Map.of("error", e.getMessage()));
                }
                return;
            }
            String cmd = switch (action) {
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

    public void apiRegions(HttpExchange ex) throws IOException { regions.apiRegions(ex); }

    public void apiNpcs(HttpExchange ex) throws IOException { regions.apiNpcs(ex); }
}

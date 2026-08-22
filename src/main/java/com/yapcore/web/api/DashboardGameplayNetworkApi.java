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
                    DashboardNetworkSnapshotWriters.saveMapSettings(root, interval, worlds);
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
            snap.put("hint", "POST define | flag-set | list | reload");
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
            case "define" -> {
                String name = body.getOrDefault("name", "").trim();
                String world = body.getOrDefault("world", "world").trim();
                if (name.isEmpty()) {
                    yield null;
                }
                yield "region define " + name + " at " + world + " "
                        + body.getOrDefault("x1", "0") + " "
                        + body.getOrDefault("y1", "0") + " "
                        + body.getOrDefault("z1", "0") + " "
                        + body.getOrDefault("x2", "0") + " "
                        + body.getOrDefault("y2", "255") + " "
                        + body.getOrDefault("z2", "0");
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

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
            snap.put("hint", "POST reload | prune | lookup | lookup-radius | rollback | restore | save-settings");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "status").toLowerCase();
            if ("save-settings".equals(action)) {
                try {
                    Boolean logging = body.containsKey("loggingEnabled")
                            ? !"false".equalsIgnoreCase(body.get("loggingEnabled")) : null;
                    Boolean blocks = body.containsKey("logBlocks")
                            ? !"false".equalsIgnoreCase(body.get("logBlocks")) : null;
                    Boolean containers = body.containsKey("logContainers")
                            ? !"false".equalsIgnoreCase(body.get("logContainers")) : null;
                    Integer prune = null;
                    if (body.containsKey("pruneDays")) {
                        prune = Integer.parseInt(body.get("pruneDays"));
                    }
                    DashboardNetworkSnapshotWriters.saveProtectSettings(root, logging, blocks, containers, prune);
                    server.executeCommand("yapprotect reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                } catch (Exception e) {
                    DashboardHttp.json(ex, 500, Map.of("error", e.getMessage()));
                }
                return;
            }
            String cmd = switch (action) {
                case "reload" -> "yapprotect reload";
                case "prune" -> "yapprotect prune " + body.getOrDefault("days", "30");
                case "lookup" -> "yapprotect dash-lookup user " + body.getOrDefault("player", "Steve") + " "
                        + body.getOrDefault("limit", "10");
                case "lookup-radius" -> "yapprotect dash-lookup radius " + body.getOrDefault("radius", "16") + " "
                        + body.getOrDefault("limit", "25");
                case "rollback" -> {
                    if (body.containsKey("player")) {
                        yield "yapprotect rollback user " + body.get("player") + " "
                                + body.getOrDefault("duration", "7d");
                    }
                    yield "yapprotect rollback " + body.getOrDefault("id", "0");
                }
                case "restore" -> {
                    if (body.containsKey("player")) {
                        yield "yapprotect restore user " + body.get("player") + " "
                                + body.getOrDefault("duration", "7d");
                    }
                    yield "yapprotect restore " + body.getOrDefault("id", "0");
                }
                default -> "yapprotect status";
            };
            String result = server.executeCommand(cmd);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("command", cmd);
            resp.put("result", result == null ? "" : result);
            if ("lookup".equals(action) || "lookup-radius".equals(action)) {
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
            if ("save-brush".equals(action)) {
                try {
                    int max = Integer.parseInt(body.getOrDefault("maxRadius", "16"));
                    DashboardNetworkSnapshotWriters.saveWorldBrushMax(root, max);
                    server.executeCommand("yapworld reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "maxRadius", max));
                } catch (Exception e) {
                    DashboardHttp.json(ex, 500, Map.of("error", e.getMessage()));
                }
                return;
            }
            String cmd = switch (action) {
                case "reload" -> "yapworld reload";
                case "load" -> "yapworld load " + world;
                case "unload" -> "yapworld unload " + world;
                case "pregen-status" -> "yapworld pregen status";
                case "status" -> "yapworld status";
                case "schem-list" -> "yapworld schem list";
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
            if ("save-settings".equals(action)) {
                try {
                    DashboardNetworkSnapshotWriters.saveChatSettings(root,
                            body.get("defaultChannel"),
                            body.containsKey("slowModeSeconds")
                                    ? Integer.parseInt(body.get("slowModeSeconds")) : null,
                            body.containsKey("filterEnabled")
                                    ? !"false".equalsIgnoreCase(body.get("filterEnabled")) : null,
                            body.containsKey("networkEnabled")
                                    ? !"false".equalsIgnoreCase(body.get("networkEnabled")) : null);
                    server.executeCommand("yapchat reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                } catch (Exception e) {
                    DashboardHttp.json(ex, 500, Map.of("error", e.getMessage()));
                }
                return;
            }
            if ("save-channel-format".equals(action)) {
                try {
                    String channel = body.getOrDefault("channel", "global");
                    String format = body.getOrDefault("format", "");
                    DashboardNetworkSnapshotWriters.saveChatChannelFormat(root, channel, format);
                    server.executeCommand("yapchat reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "channel", channel));
                } catch (Exception e) {
                    DashboardHttp.json(ex, 500, Map.of("error", e.getMessage()));
                }
                return;
            }
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
            snap.put("hint", "POST kick | ban | tempban | ipban | mute | tempmute | warn | kick | tp | tp-to | tp-spawn | set-group | promote | history | check | banlist");
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
                case "kick" -> {
                    String p = body.getOrDefault("player", "");
                    String r = body.getOrDefault("reason", "Kicked via dashboard");
                    yield p.isBlank() ? null : "kick " + p + " " + r;
                }
                case "ban" -> {
                    String p = body.getOrDefault("player", "");
                    String r = body.getOrDefault("reason", "Banned via dashboard");
                    yield p.isBlank() ? null : "ban " + p + " " + r;
                }
                case "tempban", "timeout" -> {
                    String p = body.getOrDefault("player", "");
                    String d = body.getOrDefault("duration", "1d");
                    String r = body.getOrDefault("reason", "Timed ban via dashboard");
                    yield p.isBlank() ? null : "tempban " + p + " " + d + " " + r;
                }
                case "ipban" -> {
                    String t = body.getOrDefault("ip", body.getOrDefault("player", ""));
                    String r = body.getOrDefault("reason", "IP banned via dashboard");
                    yield t.isBlank() ? null : "ipban " + t + " " + r;
                }
                case "unbanip" -> {
                    String ip = body.getOrDefault("ip", body.getOrDefault("player", ""));
                    yield ip.isBlank() ? null : "unbanip " + ip;
                }
                case "mute" -> {
                    String p = body.getOrDefault("player", "");
                    String r = body.getOrDefault("reason", "Muted via dashboard");
                    yield p.isBlank() ? null : "mute " + p + " " + r;
                }
                case "tempmute" -> {
                    String p = body.getOrDefault("player", "");
                    String d = body.getOrDefault("duration", "1h");
                    String r = body.getOrDefault("reason", "Timed mute via dashboard");
                    yield p.isBlank() ? null : "tempmute " + p + " " + d + " " + r;
                }
                case "unmute" -> "unmute " + body.getOrDefault("player", "Steve");
                case "warn" -> {
                    String p = body.getOrDefault("player", "");
                    String r = body.getOrDefault("reason", "Warned via dashboard");
                    yield p.isBlank() ? null : "warn " + p + " " + r;
                }
                case "check", "modcheck" -> "modcheck " + body.getOrDefault("player", "Steve");
                case "banlist" -> "banlist " + body.getOrDefault("limit", "25");
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
            snap.put("hint", "POST reload | applypack | user-info | set-group | promote | demote");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            String cmd = switch (action) {
                case "reload" -> "yapperm reload";
                case "applypack" -> "yapperm applypack";
                case "user-info" -> {
                    String p = body.getOrDefault("player", "");
                    yield p.isBlank() ? null : "yapperm user " + p + " info";
                }
                case "set-group" -> {
                    String p = body.getOrDefault("player", "");
                    String g = body.getOrDefault("group", "default");
                    yield p.isBlank() ? null : "yapperm user " + p + " parent set " + g;
                }
                case "promote" -> {
                    String p = body.getOrDefault("player", "");
                    yield p.isBlank() ? null : "promote " + p;
                }
                case "demote" -> {
                    String p = body.getOrDefault("player", "");
                    yield p.isBlank() ? null : "demote " + p;
                }
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

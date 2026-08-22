package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.plugin.PluginManager;
import com.yapcore.ranks.YapRanks;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardEssentialsSnapshot;
import com.yapcore.web.DashboardLinkSnapshot;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.DashboardProtectLookup;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardGameplayApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    private final DashboardGameplayNetworkApi network;
    private final DashboardGameplayOpsApi ops;

    public DashboardGameplayApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
        this.network = new DashboardGameplayNetworkApi(server, auth);
        this.ops = new DashboardGameplayOpsApi(server, auth);
    }

    public void apiVehicles(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            DashboardHttp.json(ex, 200, Map.of(
                    "types", List.of(
                            "chassis", "buggy", "hoverbike", "truck_4x4", "monster_truck",
                            "sport_car", "hypercar", "lambo", "ferrari", "mclaren", "porsche"),
                    "hint", "POST {\"action\":\"spawn\",\"type\":\"lambo\"} or shop/upgrades commands"));
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "spawn");
            String type = body.getOrDefault("type", "buggy");
            String cmd = switch (action) {
                case "shop" -> "yapvehicle shop";
                case "upgrades" -> "yapvehicle upgrades";
                case "list" -> "yapvehicle list";
                case "types" -> "yapvehicle types";
                default -> "yapvehicle spawn " + type;
            };
            String result = server.executeCommand(cmd);
            DashboardHttp.json(ex, 200, Map.of("ok", true, "command", cmd, "result", result == null ? "" : result));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiPregen(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            String result = server.executeCommand("yappregen status all");
            DashboardHttp.json(ex, 200, Map.of(
                    "ok", true,
                    "status", result == null ? "" : result,
                    "shapes", List.of("radius", "circle", "corners", "polygon", "worldborder", "selection"),
                    "hint", "POST {\"action\":\"start\",\"world\":\"world\",\"shape\":\"radius\",\"radius\":\"8\"}"));
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "status").toLowerCase();
            String world = body.getOrDefault("world", "world");
            String target = body.getOrDefault("target", "all");
            String cmd;
            switch (action) {
                case "pause" -> cmd = "yappregen pause " + target;
                case "resume" -> cmd = "yappregen resume " + target;
                case "cancel" -> cmd = "yappregen cancel " + target;
                case "status" -> cmd = "yappregen status " + target;
                case "start" -> {
                    String shape = body.getOrDefault("shape", "radius").toLowerCase();
                    cmd = switch (shape) {
                        case "circle" -> "yappregen start " + world + " circle "
                                + body.getOrDefault("radius", "128");
                        case "corners", "rect" -> "yappregen start " + world + " corners "
                                + body.getOrDefault("x1", "0") + " " + body.getOrDefault("z1", "0") + " "
                                + body.getOrDefault("x2", "128") + " " + body.getOrDefault("z2", "128");
                        case "worldborder", "border" -> "yappregen start " + world + " worldborder";
                        case "polygon" -> "yappregen start " + world + " polygon "
                                + body.getOrDefault("points", "0 0 160 0 80 160");
                        default -> "yappregen start " + world + " radius "
                                + body.getOrDefault("radius", "8");
                    };
                }
                default -> {
                    DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                    return;
                }
            }
            String result = server.executeCommand(cmd);
            DashboardHttp.json(ex, 200, Map.of("ok", true, "command", cmd, "result", result == null ? "" : result));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiRanks(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        PluginManager pm = server.getPluginManager();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            try {
                DashboardHttp.json(ex, 200, Map.of(
                        "yapPermsInstalled", YapRanks.yapPermsInstalled(pm.getPluginsDir()),
                        "applied", YapRanks.isApplied(root),
                        "autoApply", server.getConfig().isYapRanksAutoApply(),
                        "commandCount", YapRanks.loadCommands(root).size(),
                        "commands", YapRanks.loadCommands(root),
                        "groups", List.of("default", "vip", "mod", "admin"),
                        "track", "yap",
                        "hint", "POST {\"action\":\"apply\"} or {\"action\":\"apply\",\"force\":\"true\"}"));
            } catch (Exception e) {
                DashboardHttp.json(ex, 500, Map.of("error", e.getMessage() == null ? "ranks status failed" : e.getMessage()));
            }
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "apply").toLowerCase();
            String result = switch (action) {
                case "status" -> server.executeCommand("ranks status");
                case "reset-marker", "reset" -> server.executeCommand("ranks reset-marker");
                case "show" -> server.executeCommand("ranks show");
                case "apply" -> {
                    boolean force = "true".equalsIgnoreCase(body.getOrDefault("force", "false"));
                    yield server.executeCommand(force ? "ranks apply force" : "ranks apply");
                }
                default -> "Unknown action. Use apply, status, reset-marker, show.";
            };
            DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action, "result", result == null ? "" : result));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiEssentials(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardEssentialsSnapshot.snapshot(root));
            snap.put("ok", true);
            snap.put("hint", "POST reload | broadcast | save-motd | save-rules | set-feature");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "status").toLowerCase();
            switch (action) {
                case "reload" -> {
                    String result = server.executeCommand("yapess reload");
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true, "action", action, "command", "yapess reload",
                            "result", result == null ? "" : result));
                }
                case "broadcast" -> {
                    String message = body.getOrDefault("message", "Server announcement");
                    String cmd = "broadcast " + message;
                    String result = server.executeCommand(cmd);
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true, "action", action, "command", cmd,
                            "result", result == null ? "" : result));
                }
                case "save-motd" -> {
                    List<String> lines = DashboardApiUtil.splitLines(body.getOrDefault("text", ""));
                    DashboardEssentialsSnapshot.saveMotd(root, lines);
                    server.executeCommand("yapess reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action, "lines", lines.size()));
                }
                case "save-rules" -> {
                    List<String> lines = DashboardApiUtil.splitLines(body.getOrDefault("text", ""));
                    DashboardEssentialsSnapshot.saveRules(root, lines);
                    server.executeCommand("yapess reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action, "lines", lines.size()));
                }
                case "set-feature" -> {
                    String feature = body.getOrDefault("feature", "").trim();
                    if (feature.isEmpty()) {
                        DashboardHttp.json(ex, 400, Map.of("error", "feature required"));
                        return;
                    }
                    boolean enabled = !"false".equalsIgnoreCase(body.getOrDefault("enabled", "true"));
                    DashboardEssentialsSnapshot.saveFeature(root, feature, enabled);
                    server.executeCommand("yapess reload");
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true, "action", action, "feature", feature, "enabled", enabled));
                }
                default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiLink(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        String linkHome = server.getConfig().getLinkEmbedHome();
        boolean linkEmbed = server.getConfig().isLinkEmbed();
        boolean velocity = server.getConfig().isVelocityEnabled();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardLinkSnapshot.snapshot(root, linkHome, linkEmbed, velocity));
            snap.put("ok", true);
            snap.put("installHint",
                    "gradle :yap-link-plugin-chat-bridge:installIntoLinkPlugins "
                            + ":yap-link-plugin-mod-sync:installIntoLinkPlugins "
                            + ":yap-link-plugin-server-selector:installIntoLinkPlugins");
            snap.put("hint", "POST save-selector | save-flags (restart or link reload to apply flags)");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            switch (action) {
                case "save-selector" -> {
                    String hub = body.getOrDefault("hubServer", "lobby");
                    boolean sessionLock = !"false".equalsIgnoreCase(body.getOrDefault("sessionLock", "true"));
                    DashboardLinkSnapshot.saveSelectorConfig(root, linkHome, hub, sessionLock);
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true, "action", action, "hubServer", hub, "sessionLockEnabled", sessionLock,
                            "note", "Restart YaP Link (or embedded link) to pick up selector changes."));
                }
                case "save-flags" -> {
                    Boolean plugins = body.containsKey("pluginsEnabled")
                            ? !"false".equalsIgnoreCase(body.get("pluginsEnabled"))
                            : null;
                    Boolean chatRelay = body.containsKey("chatRelayEnabled")
                            ? !"false".equalsIgnoreCase(body.get("chatRelayEnabled"))
                            : null;
                    DashboardLinkSnapshot.saveLinkFlags(root, linkHome, plugins, chatRelay);
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("ok", true);
                    resp.put("action", action);
                    if (plugins != null) {
                        resp.put("pluginsEnabled", plugins);
                    }
                    if (chatRelay != null) {
                        resp.put("chatRelayEnabled", chatRelay);
                    }
                    resp.put("note", "Run link console reload or restart YaP Link to apply link.properties.");
                    DashboardHttp.json(ex, 200, resp);
                }
                default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiProtect(HttpExchange ex) throws IOException { ops.apiProtect(ex); }

    public void apiWorld(HttpExchange ex) throws IOException { ops.apiWorld(ex); }

    public void apiChat(HttpExchange ex) throws IOException { ops.apiChat(ex); }

    public void apiModeration(HttpExchange ex) throws IOException { ops.apiModeration(ex); }

    public void apiPerms(HttpExchange ex) throws IOException { ops.apiPerms(ex); }

    public void apiPlayerdata(HttpExchange ex) throws IOException { ops.apiPlayerdata(ex); }

    public void apiDiscord(HttpExchange ex) throws IOException { network.apiDiscord(ex); }

    public void apiTab(HttpExchange ex) throws IOException { network.apiTab(ex); }

    public void apiMap(HttpExchange ex) throws IOException { network.apiMap(ex); }

    public void apiGuard(HttpExchange ex) throws IOException { network.apiGuard(ex); }

    public void apiRegions(HttpExchange ex) throws IOException { network.apiRegions(ex); }

    public void apiNpcs(HttpExchange ex) throws IOException { network.apiNpcs(ex); }
}

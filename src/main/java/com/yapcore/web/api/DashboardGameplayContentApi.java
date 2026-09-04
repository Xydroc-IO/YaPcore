package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.plugin.PluginManager;
import com.yapcore.ranks.YapRanks;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardEssentialsSnapshot;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dashboard routes: vehicles, pregen, ranks, essentials, disasters, stacker. */
public final class DashboardGameplayContentApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardGameplayContentApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
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

    public void apiDisasters(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(com.yapcore.web.DashboardDisastersSnapshot.snapshot(root));
            String live = server.executeCommand("yapdisaster status");
            snap.put("ok", true);
            snap.put("liveStatus", live == null ? "" : live);
            snap.put("hint", "POST save-settings | reload | start | stop | random | site-add | site-remove | site-erupt");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "status").toLowerCase(Locale.ROOT);
            try {
                switch (action) {
                    case "save-settings" -> {
                        com.yapcore.web.DashboardDisastersSnapshot.saveSettings(root, body);
                        String reload = server.executeCommand("yapdisaster reload");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true,
                                "action", action,
                                "reload", reload == null ? "" : reload));
                    }
                    case "reload" -> {
                        String result = server.executeCommand("yapdisaster reload");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "result", result == null ? "" : result));
                    }
                    case "start" -> {
                        String type = body.getOrDefault("type", "thunder");
                        String seconds = body.getOrDefault("seconds", "120");
                        String world = body.getOrDefault("world", "");
                        String cmd = "yapdisaster " + type + " " + seconds
                                + (world.isBlank() ? "" : " " + world);
                        String result = server.executeCommand(cmd);
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "command", cmd,
                                "result", result == null ? "" : result));
                    }
                    case "stop" -> {
                        String world = body.getOrDefault("world", "");
                        String cmd = "yapdisaster stop" + (world.isBlank() ? "" : " " + world);
                        String result = server.executeCommand(cmd);
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "command", cmd,
                                "result", result == null ? "" : result));
                    }
                    case "random" -> {
                        String mode = body.getOrDefault("mode", "status").toLowerCase(Locale.ROOT);
                        String cmd = switch (mode) {
                            case "on", "enable" -> "yapdisaster random on";
                            case "off", "disable" -> "yapdisaster random off";
                            case "now" -> {
                                String type = body.getOrDefault("type", "");
                                String world = body.getOrDefault("world", "");
                                yield "yapdisaster random now"
                                        + (type.isBlank() ? "" : " " + type)
                                        + (world.isBlank() ? "" : " " + world);
                            }
                            default -> "yapdisaster random status";
                        };
                        String result = server.executeCommand(cmd);
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "command", cmd,
                                "result", result == null ? "" : result));
                    }
                    case "site-add" -> {
                        String id = body.getOrDefault("id", "");
                        String world = body.getOrDefault("world", "world");
                        double x = Double.parseDouble(body.getOrDefault("x", "0"));
                        double y = Double.parseDouble(body.getOrDefault("y", "64"));
                        double z = Double.parseDouble(body.getOrDefault("z", "0"));
                        boolean dormant = "true".equalsIgnoreCase(body.getOrDefault("dormant", "false"));
                        com.yapcore.web.DashboardDisastersSnapshot.upsertSite(root, id, world, x, y, z, dormant);
                        String reload = server.executeCommand("yapdisaster reload");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "id", id,
                                "reload", reload == null ? "" : reload));
                    }
                    case "site-remove" -> {
                        String id = body.getOrDefault("id", "");
                        com.yapcore.web.DashboardDisastersSnapshot.removeSite(root, id);
                        String reload = server.executeCommand("yapdisaster reload");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "id", id,
                                "reload", reload == null ? "" : reload));
                    }
                    case "site-erupt" -> {
                        String id = body.getOrDefault("id", "");
                        String seconds = body.getOrDefault("seconds", "120");
                        String cmd = "yapdisaster site erupt " + id + " " + seconds;
                        String result = server.executeCommand(cmd);
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "command", cmd,
                                "result", result == null ? "" : result));
                    }
                    case "status" -> {
                        String result = server.executeCommand("yapdisaster status");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "result", result == null ? "" : result));
                    }
                    default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                }
            } catch (Exception e) {
                DashboardHttp.json(ex, 500, Map.of(
                        "error", e.getMessage() == null ? "disasters action failed" : e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiStacker(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(com.yapcore.web.DashboardStackerSnapshot.snapshot(root));
            String status = server.executeCommand("yapstacker status");
            String stats = server.executeCommand("yapstacker stats");
            snap.put("ok", true);
            snap.put("status", status == null ? "" : status);
            snap.put("stats", stats == null ? "" : stats);
            snap.put("hint", "POST save-settings | reload | status | stats");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "status").toLowerCase(Locale.ROOT);
            try {
                switch (action) {
                    case "save-settings" -> {
                        com.yapcore.web.DashboardStackerSnapshot.saveSettings(root, body);
                        String reload = server.executeCommand("yapstacker reload");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true,
                                "action", action,
                                "reload", reload == null ? "" : reload));
                    }
                    case "reload" -> {
                        String result = server.executeCommand("yapstacker reload");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "result", result == null ? "" : result));
                    }
                    case "stats" -> {
                        String result = server.executeCommand("yapstacker stats");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "result", result == null ? "" : result));
                    }
                    default -> {
                        String result = server.executeCommand("yapstacker status");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", "status", "result", result == null ? "" : result));
                    }
                }
            } catch (Exception e) {
                DashboardHttp.json(ex, 500, Map.of(
                        "ok", false,
                        "error", e.getMessage() == null ? "stacker action failed" : e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }
}

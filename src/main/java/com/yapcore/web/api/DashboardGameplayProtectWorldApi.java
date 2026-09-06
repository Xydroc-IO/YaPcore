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

/** Dashboard routes: protect and world. */
final class DashboardGameplayProtectWorldApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    DashboardGameplayProtectWorldApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    void apiProtect(HttpExchange ex) throws IOException {
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
            String cursorArg = body.containsKey("cursor") && !body.get("cursor").isBlank()
                    ? " --cursor " + body.get("cursor") : "";
            String cmd = switch (action) {
                case "reload" -> "yapprotect reload";
                case "prune" -> "yapprotect prune " + body.getOrDefault("days", "30");
                case "lookup" -> "yapprotect dash-lookup user " + body.getOrDefault("player", "Steve") + " "
                        + body.getOrDefault("limit", "10") + cursorArg;
                case "lookup-radius" -> "yapprotect dash-lookup radius " + body.getOrDefault("radius", "16") + " "
                        + body.getOrDefault("limit", "25") + cursorArg;
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
                DashboardProtectLookup.Page page = DashboardProtectLookup.parsePage(result);
                resp.put("lookupRows", page.rows());
                resp.put("nextCursor", page.nextCursor());
                resp.put("hasMore", page.hasMore());
            }
            DashboardHttp.json(ex, 200, resp);
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    void apiWorld(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.world(root));
            String status = server.executeCommand("yapworld status");
            snap.put("ok", true);
            snap.put("status", status == null ? "" : status);
            snap.put("hint", "POST reload | create | load | unload | pregen-status");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "status").toLowerCase();
            String world = sanitizeWorldToken(body.getOrDefault("world", "world"));
            if (world == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "invalid world name"));
                return;
            }
            if ("save-brush".equals(action) || "save_brush".equals(action)) {
                try {
                    int max = Integer.parseInt(body.getOrDefault("maxRadius", body.getOrDefault("max_radius", "16")));
                    DashboardNetworkSnapshotWriters.saveWorldBrushMax(root, max);
                    server.executeCommand("yapworld reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "maxRadius", max));
                } catch (Exception e) {
                    DashboardHttp.json(ex, 500, Map.of("error", e.getMessage()));
                }
                return;
            }
            if ("create".equals(action) || "new".equals(action)) {
                StringBuilder cmd = new StringBuilder("yapworld create ").append(world);
                appendCreateFlag(cmd, "--type", sanitizeEnumToken(body.get("type"),
                        "NORMAL", "FLAT", "LARGE_BIOMES", "AMPLIFIED"));
                appendCreateFlag(cmd, "--env", sanitizeEnvToken(body.get("environment") != null
                        ? body.get("environment") : body.get("env")));
                String seedRaw = body.get("seed");
                if (seedRaw != null && !seedRaw.isBlank()) {
                    try {
                        long seed = Long.parseLong(seedRaw.trim());
                        cmd.append(" --seed ").append(seed);
                    } catch (NumberFormatException e) {
                        DashboardHttp.json(ex, 400, Map.of("error", "invalid seed"));
                        return;
                    }
                }
                String gen = sanitizeGeneratorToken(body.get("generator") != null
                        ? body.get("generator") : body.get("gen"));
                if (gen != null) {
                    cmd.append(" --generator ").append(gen);
                }
                if ("false".equalsIgnoreCase(body.getOrDefault("structures", "true"))
                        || "0".equals(body.get("structures"))
                        || "no".equalsIgnoreCase(body.getOrDefault("structures", ""))) {
                    cmd.append(" --no-structures");
                }
                String result = server.executeCommand(cmd.toString());
                DashboardHttp.json(ex, 200, Map.of("ok", true, "command", cmd.toString(),
                        "result", result == null ? "" : result));
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

    private static void appendCreateFlag(StringBuilder cmd, String flag, String value) {
        if (value != null && !value.isBlank()) {
            cmd.append(' ').append(flag).append(' ').append(value);
        }
    }

    /** World folder name: letters, digits, underscore, hyphen. */
    private static String sanitizeWorldToken(String raw) {
        if (raw == null) {
            return null;
        }
        String n = raw.trim();
        if (n.isEmpty() || n.length() > 64 || !n.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        return n;
    }

    private static String sanitizeEnumToken(String raw, String... allowed) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if ("SUPERFLAT".equals(t) || "SUPER_FLAT".equals(t)) {
            t = "FLAT";
        }
        if ("LARGEBIOMES".equals(t) || "LARGE_BIOME".equals(t)) {
            t = "LARGE_BIOMES";
        }
        for (String a : allowed) {
            if (a.equalsIgnoreCase(t)) {
                return a;
            }
        }
        return null;
    }

    private static String sanitizeEnvToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String e = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (e) {
            case "NORMAL", "OVERWORLD", "WORLD" -> "overworld";
            case "NETHER", "HELL" -> "nether";
            case "THE_END", "END", "THEEND" -> "end";
            default -> null;
        };
    }

    private static String sanitizeGeneratorToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String g = raw.trim();
        if (g.length() > 64 || !g.matches("[A-Za-z0-9_.:-]+")) {
            return null;
        }
        return g;
    }
}

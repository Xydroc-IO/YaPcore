package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.plugin.PluginManager;
import com.yapcore.ranks.YapRanks;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardAbilitiesSnapshot;
import com.yapcore.web.DashboardEssentialsSnapshot;
import com.yapcore.web.DashboardFactionsSnapshot;
import com.yapcore.web.DashboardGuildsSnapshot;
import com.yapcore.web.DashboardGamesSnapshot;
import com.yapcore.web.DashboardLinkSnapshot;
import com.yapcore.web.DashboardMmoSnapshot;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.DashboardProtectLookup;
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
import java.util.Map;

public final class DashboardGameplayApi {

    private static final Gson GSON = new Gson();

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
            var linkProc = server.getLinkProcess();
            snap.put("ok", true);
            snap.put("linkRunning", linkProc.isRunning());
            snap.put("linkJar", linkProc.resolveJar().toString());
            snap.put("linkJarPresent", java.nio.file.Files.isRegularFile(linkProc.resolveJar()));
            snap.put("linkConsoleHint", "GET /api/link/console · SSE /api/link/console/stream");
            snap.put("installHint",
                    "gradle :yap-link-plugin-chat-bridge:installIntoLinkPlugins "
                            + ":yap-link-plugin-mod-sync:installIntoLinkPlugins "
                            + ":yap-link-plugin-server-selector:installIntoLinkPlugins");
            snap.put("hint", "POST start | stop | command | enable-backend-forwarding | save-selector | save-flags | save-proxy | save-servers");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            String rawBody = DashboardHttp.readBody(ex);
            Map<String, String> body = TinyJson.parseFlatObject(rawBody);
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
                    Map<String, Object> resp = linkSaveResponse(action, root, linkHome);
                    if (plugins != null) {
                        resp.put("pluginsEnabled", plugins);
                    }
                    if (chatRelay != null) {
                        resp.put("chatRelayEnabled", chatRelay);
                    }
                    DashboardHttp.json(ex, 200, resp);
                }
                case "save-proxy" -> {
                    Map<String, String> updates = linkProxyUpdatesFromBody(body);
                    if (updates.isEmpty()) {
                        DashboardHttp.json(ex, 400, Map.of("error", "no proxy fields to save"));
                        return;
                    }
                    DashboardLinkSnapshot.saveProxySettings(root, linkHome, updates);
                    DashboardHttp.json(ex, 200, linkSaveResponse(action, root, linkHome));
                }
                case "save-servers" -> {
                    try {
                        DashboardLinkSnapshot.saveServersFromJson(root, linkHome, rawBody);
                        DashboardHttp.json(ex, 200, linkSaveResponse(action, root, linkHome));
                    } catch (IOException e) {
                        DashboardHttp.json(ex, 400, Map.of("error", e.getMessage()));
                    }
                }
                case "start" -> {
                    try {
                        server.getLinkProcess().start();
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "running", server.getLinkProcess().isRunning()));
                    } catch (IOException e) {
                        DashboardHttp.json(ex, 500, Map.of("error", e.getMessage()));
                    }
                }
                case "stop" -> {
                    server.getLinkProcess().stop();
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true, "action", action, "running", server.getLinkProcess().isRunning()));
                }
                case "command" -> {
                    String cmd = body.getOrDefault("command", "").trim();
                    if (cmd.isEmpty()) {
                        DashboardHttp.json(ex, 400, Map.of("error", "command required"));
                        return;
                    }
                    String result = server.getLinkProcess().dispatchCommand(cmd);
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action, "result", result));
                }
                case "enable-backend-forwarding" -> {
                    try {
                        Path script = root.resolve("scripts/setup-velocity-forwarding.sh");
                        if (!java.nio.file.Files.isRegularFile(script)) {
                            DashboardHttp.json(ex, 404, Map.of("error", "missing scripts/setup-velocity-forwarding.sh"));
                            return;
                        }
                        var lp = server.getLinkProcess();
                        lp.appendLog("[Link] Running setup-velocity-forwarding.sh --enable…\n");
                        ProcessBuilder pb = new ProcessBuilder("bash", script.toString(), "--enable");
                        pb.directory(root.toFile());
                        pb.redirectErrorStream(true);
                        Process p = pb.start();
                        String out = new String(p.getInputStream().readAllBytes());
                        p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
                        lp.appendLog(out + "\nexit=" + p.exitValue() + "\n");
                        DashboardHttp.json(ex, 200, Map.of(
                                "ok", true, "action", action, "exit", p.exitValue(), "output", out));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        DashboardHttp.json(ex, 500, Map.of("error", "interrupted"));
                    }
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

    public void apiTebex(HttpExchange ex) throws IOException { network.apiTebex(ex); }

    public void apiTab(HttpExchange ex) throws IOException { network.apiTab(ex); }

    public void apiMap(HttpExchange ex) throws IOException { network.apiMap(ex); }

    public void apiGuard(HttpExchange ex) throws IOException { network.apiGuard(ex); }

    public void apiRegions(HttpExchange ex) throws IOException { network.apiRegions(ex); }

    public void apiNpcs(HttpExchange ex) throws IOException { network.apiNpcs(ex); }

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
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        Path root = server.getRootDir();
        Map<String, Object> snap = new LinkedHashMap<>(DashboardFactionsSnapshot.snapshot(root));
        snap.put("onlinePlayers", server.getOnlinePlayers());
        if (server.isRunning()) {
            String live = server.executeCommand("yapfactions snapshot json");
            if (live != null && live.contains("YAPFACTIONS_JSON:")) {
                int idx = live.indexOf("YAPFACTIONS_JSON:");
                String payload = live.substring(idx + "YAPFACTIONS_JSON:".length()).trim();
                snap.put("live", payload);
            }
        }
        DashboardHttp.json(ex, 200, snap);
    }

    public void apiGuilds(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        Path root = server.getRootDir();
        Map<String, Object> snap = new LinkedHashMap<>(DashboardGuildsSnapshot.snapshot(root));
        snap.put("onlinePlayers", server.getOnlinePlayers());
        if (server.isRunning()) {
            String live = server.executeCommand("yapguilds snapshot json");
            if (live != null && live.contains("YAPGUILDS_JSON:")) {
                int idx = live.indexOf("YAPGUILDS_JSON:");
                String payload = live.substring(idx + "YAPGUILDS_JSON:".length()).trim();
                snap.put("live", payload);
            }
        }
        DashboardHttp.json(ex, 200, snap);
    }

    public void apiGames(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        Path root = server.getRootDir();
        Map<String, Object> snap = new LinkedHashMap<>(DashboardGamesSnapshot.snapshot(root));
        snap.put("onlinePlayers", server.getOnlinePlayers());
        if (server.isRunning()) {
            String live = server.executeCommand("ygames snapshot json");
            if (live != null && live.contains("YAPGAMES_JSON:")) {
                int idx = live.indexOf("YAPGAMES_JSON:");
                String payload = live.substring(idx + "YAPGAMES_JSON:".length()).trim();
                snap.put("live", payload);
            }
        }
        DashboardHttp.json(ex, 200, snap);
    }

    private Map<String, Object> linkSaveResponse(String action, Path root, String linkHome) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("action", action);
        var linkProc = server.getLinkProcess();
        if (linkProc.isRunning()) {
            String reload = linkProc.dispatchCommand("reload");
            resp.put("reloaded", true);
            resp.put("reloadResult", reload);
            resp.put("note", "Saved link.properties and sent reload to running Link.");
        } else {
            resp.put("note", "Saved link.properties — start Link or run reload when ready.");
        }
        resp.put("config", DashboardLinkSnapshot.snapshot(
                root, linkHome, server.getConfig().isLinkEmbed(), server.getConfig().isVelocityEnabled()));
        return resp;
    }

    private static Map<String, String> linkProxyUpdatesFromBody(Map<String, String> body) {
        Map<String, String> updates = new LinkedHashMap<>();
        putIfPresent(body, updates, "bind");
        putIfPresent(body, updates, "motd");
        putIfPresent(body, updates, "max-players", "maxPlayers");
        putBoolIfPresent(body, updates, "online-mode", "onlineMode");
        putIfPresent(body, updates, "public-host", "publicHost");
        putIfPresent(body, updates, "public-port", "publicPort");
        putBoolIfPresent(body, updates, "ping-passthrough", "pingPassthrough");
        putBoolIfPresent(body, updates, "aggregate-player-count", "aggregatePlayerCount");
        putBoolIfPresent(body, updates, "global-tab-list", "globalTabList");
        putBoolIfPresent(body, updates, "chat-relay-enabled", "chatRelayEnabled");
        putIfPresent(body, updates, "chat-relay-channel", "chatRelayChannel");
        putIfPresent(body, updates, "chat-relay-format", "chatRelayFormat");
        putBoolIfPresent(body, updates, "chat-join-announce", "chatJoinAnnounce");
        putBoolIfPresent(body, updates, "plugins-enabled", "pluginsEnabled");
        putBoolIfPresent(body, updates, "enable-server-command", "enableServerCommand");
        putBoolIfPresent(body, updates, "bedrock-enabled", "bedrockEnabled");
        putIfPresent(body, updates, "bedrock-bind", "bedrockBind");
        putIfPresent(body, updates, "bedrock-backend", "bedrockBackend");
        putIfPresent(body, updates, "floodgate-key-file", "floodgateKeyFile");
        putIfPresent(body, updates, "connect-timeout-ms", "connectTimeoutMs");
        putIfPresent(body, updates, "login-timeout-ms", "loginTimeoutMs");
        return updates;
    }

    private static void putIfPresent(Map<String, String> body, Map<String, String> out, String propKey) {
        putIfPresent(body, out, propKey, propKey);
    }

    private static void putIfPresent(Map<String, String> body, Map<String, String> out, String propKey, String bodyKey) {
        if (body.containsKey(bodyKey)) {
            out.put(propKey, body.get(bodyKey));
        } else if (body.containsKey(propKey)) {
            out.put(propKey, body.get(propKey));
        }
    }

    private static void putBoolIfPresent(Map<String, String> body, Map<String, String> out, String propKey, String bodyKey) {
        String val = body.containsKey(bodyKey) ? body.get(bodyKey) : body.get(propKey);
        if (val != null) {
            out.put(propKey, "false".equalsIgnoreCase(val.trim()) ? "false" : "true");
        }
    }
}

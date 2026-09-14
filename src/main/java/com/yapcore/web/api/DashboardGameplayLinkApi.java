package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.config.BedrockModeApplier;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardLinkSnapshot;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dashboard routes: YaP Link / proxy embed. */
public final class DashboardGameplayLinkApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardGameplayLinkApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
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
            snap.put("hint", "POST start | stop | command | set-velocity-forwarding | set-bedrock-mode | save-proxy | …");
            snap.put("velocityEnabled", velocity);
            snap.put("backendHealth", com.yapcore.fleet.link.BackendHealthBridge.collectMaps(
                    root, linkHome, linkProc.isRunning()));
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
                case "set-bedrock-mode" -> {
                    String mode = body.getOrDefault("bedrockMode",
                            body.getOrDefault("bedrock-mode", "native"));
                    if (server.isRunning() || server.getLinkProcess().isRunning()
                            || server.getGeyserProcess().isRunning()) {
                        DashboardHttp.json(ex, 409, Map.of(
                                "ok", false,
                                "error", "Stop the game stack (and Link/Geyser) before changing Bedrock path.",
                                "hint", "Toggle → Save → then Start servers."));
                        return;
                    }
                    Path linkDir = DashboardLinkSnapshot.resolveHome(root, linkHome);
                    Map<String, Object> applied = BedrockModeApplier.apply(
                            root, linkDir, server.getConfig().getFile(), mode);
                    try {
                        server.getConfig().load();
                    } catch (IOException e) {
                        applied.put("configReloadError", e.getMessage());
                    }
                    applied.put("action", action);
                    applied.put("note", "Bedrock path set to " + applied.get("bedrockMode")
                            + " — Link + chassis config updated. Start servers when ready.");
                    Map<String, Object> snap = new LinkedHashMap<>(DashboardLinkSnapshot.snapshot(
                            root, linkHome, linkEmbed, velocity));
                    applied.put("config", snap);
                    DashboardHttp.json(ex, 200, applied);
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
                    // Legacy alias — prefer set-velocity-forwarding with enabled=true|false
                    runVelocityForwardingScript(ex, root, true);
                }
                case "set-velocity-forwarding", "disable-backend-forwarding" -> {
                    boolean enable;
                    if ("disable-backend-forwarding".equals(action)) {
                        enable = false;
                    } else {
                        String raw = body.getOrDefault("enabled",
                                body.getOrDefault("velocityEnabled",
                                        body.getOrDefault("enable", "true")));
                        enable = !"false".equalsIgnoreCase(raw) && !"0".equals(raw)
                                && !"disable".equalsIgnoreCase(raw) && !"off".equalsIgnoreCase(raw);
                    }
                    runVelocityForwardingScript(ex, root, enable);
                }
                default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private void runVelocityForwardingScript(HttpExchange ex, Path root, boolean enable) throws IOException {
        try {
            Path script = root.resolve("scripts/setup-velocity-forwarding.sh");
            if (!java.nio.file.Files.isRegularFile(script)) {
                DashboardHttp.json(ex, 404, Map.of("error", "missing scripts/setup-velocity-forwarding.sh"));
                return;
            }
            String flag = enable ? "--enable" : "--disable";
            var lp = server.getLinkProcess();
            lp.appendLog("[Link] Running setup-velocity-forwarding.sh " + flag + "…\n");
            ProcessBuilder pb = new ProcessBuilder("bash", script.toString(), flag);
            pb.directory(root.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            lp.appendLog(out + "\nexit=" + p.exitValue() + "\n");
            try {
                server.getConfig().load();
            } catch (IOException ignored) {
                // disk updated; in-memory may refresh on next start
            }
            DashboardHttp.json(ex, 200, Map.of(
                    "ok", true,
                    "action", "set-velocity-forwarding",
                    "velocityEnabled", enable,
                    "exit", p.exitValue(),
                    "output", out,
                    "note", enable
                            ? "Modern forwarding ON — join via YaP Link :25565."
                            : "Modern forwarding OFF — direct chassis :25566; restart Folia to apply."));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DashboardHttp.json(ex, 500, Map.of("error", "interrupted"));
        }
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
        // motd / online-mode / public-host / public-port: chassis-owned, mirrored via
        // LinkIdentityMirror. max-players is Link-owned (network cap) — accepted below.
        putIfPresent(body, updates, "max-players", "maxPlayers");
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

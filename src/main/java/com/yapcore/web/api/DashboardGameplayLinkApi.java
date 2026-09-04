package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
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

package com.yapcore.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.yapcore.client.ClientEdition;
import com.yapcore.config.ServerConfig;
import com.yapcore.console.ConsoleBus;
import com.yapcore.module.ModuleManager;
import com.yapcore.plugin.PluginManager;
import com.yapcore.resourcepack.ResourcePackManager;
import com.yapcore.server.YaPcoreServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Headless control dashboard — mirrors Swing {@code ControlPanel} over HTTP.
 * Default port {@code 8080} (pack HTTP stays on 8081).
 */
public final class WebDashboard {

    private static final Logger LOG = Logger.getLogger("YaPcore.WebDash");

    private final YaPcoreServer server;
    private final CopyOnWriteArrayList<OutputStream> sseClients = new CopyOnWriteArrayList<>();
    private final Consumer<String> consoleListener;
    private HttpServer http;
    private String token;

    public WebDashboard(YaPcoreServer server) {
        this.server = server;
        this.consoleListener = line -> broadcastSse(line);
    }

    public static WebDashboard maybeStart(YaPcoreServer server) {
        ServerConfig cfg = server.getConfig();
        if (!cfg.isWebDashboardEnabled()) {
            LOG.info("Web dashboard disabled (web-dashboard-enabled=false)");
            return null;
        }
        WebDashboard dash = new WebDashboard(server);
        try {
            dash.start();
            return dash;
        } catch (IOException e) {
            LOG.severe("Web dashboard failed to start: " + e.getMessage());
            return null;
        }
    }

    public synchronized void start() throws IOException {
        if (http != null) {
            return;
        }
        ServerConfig cfg = server.getConfig();
        ensureToken(cfg);
        String bind = cfg.getWebDashboardBind();
        if (cfg.isWebDashboardLocalhostOnly()) {
            bind = "127.0.0.1";
        }
        int port = cfg.getWebDashboardPort();
        InetSocketAddress addr = new InetSocketAddress(
                "0.0.0.0".equals(bind) ? "0.0.0.0" : bind, port);
        http = HttpServer.create(addr, 0);

        http.createContext("/", this::serveStatic);
        http.createContext("/api/status", this::apiStatus);
        http.createContext("/api/connect", this::apiConnect);
        http.createContext("/api/config", this::apiConfig);
        http.createContext("/api/server/start", this::apiStart);
        http.createContext("/api/server/stop", this::apiStop);
        http.createContext("/api/command", this::apiCommand);
        http.createContext("/api/plugins", this::apiPlugins);
        http.createContext("/api/modules", this::apiModules);
        http.createContext("/api/packs", this::apiPacks);
        http.createContext("/api/console", this::apiConsole);
        http.createContext("/api/console/stream", this::apiConsoleStream);
        http.createContext("/api/vehicles", this::apiVehicles);
        http.createContext("/api/pregen", this::apiPregen);
        http.createContext("/health", ex -> text(ex, 200, "ok"));

        http.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "yap-web-dash");
            t.setDaemon(true);
            return t;
        }));
        http.start();
        ConsoleBus.get().addListener(consoleListener);
        LOG.info("Web dashboard http://" + ("0.0.0.0".equals(bind) ? "127.0.0.1" : bind)
                + ":" + port + "/  (token required — see web-dashboard-token in config)");
        LOG.info("Dashboard login token: " + token);
    }

    public synchronized void stop() {
        ConsoleBus.get().removeListener(consoleListener);
        for (OutputStream out : sseClients) {
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
        sseClients.clear();
        if (http != null) {
            http.stop(0);
            http = null;
            LOG.info("Web dashboard stopped");
        }
    }

    private void ensureToken(ServerConfig cfg) throws IOException {
        token = cfg.getWebDashboardToken();
        if (token == null || token.isBlank()) {
            byte[] raw = new byte[16];
            new SecureRandom().nextBytes(raw);
            token = HexFormat.of().formatHex(raw);
            cfg.setWebDashboardToken(token);
            cfg.save();
            LOG.warning("Generated web-dashboard-token and saved to config/server.properties");
        }
    }

    private boolean authorized(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return token.equals(auth.substring(7).trim());
        }
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) {
            for (String part : q.split("&")) {
                int eq = part.indexOf('=');
                if (eq > 0 && "token".equals(part.substring(0, eq))) {
                    return token.equals(URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
        }
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) {
            for (String part : cookie.split(";")) {
                String p = part.trim();
                if (p.startsWith("yap_token=")) {
                    return token.equals(p.substring("yap_token=".length()));
                }
            }
        }
        return false;
    }

    private boolean requireAuth(HttpExchange ex) throws IOException {
        if (authorized(ex)) {
            return true;
        }
        json(ex, 401, Map.of("error", "unauthorized", "hint", "Send Authorization: Bearer <token>"));
        return false;
    }

    // ---- static ----

    private void serveStatic(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            path = "/index.html";
        }
        if (path.contains("..")) {
            ex.sendResponseHeaders(400, -1);
            return;
        }
        String resource = "web" + path;
        try (InputStream in = WebDashboard.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                text(ex, 404, "not found");
                return;
            }
            byte[] body = in.readAllBytes();
            Headers h = ex.getResponseHeaders();
            h.set("Content-Type", contentType(path));
            h.set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "text/html; charset=utf-8";
    }

    // ---- API ----

    private void apiStatus(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) || !requireAuth(ex)) {
            return;
        }
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long max = rt.maxMemory() / (1024 * 1024);
        ServerConfig cfg = server.getConfig();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", server.isRunning());
        m.put("players", server.getOnlinePlayers());
        m.put("maxPlayers", server.getMaxPlayers());
        m.put("heapUsedMb", used);
        m.put("heapMaxMb", max);
        m.put("ramConfigMb", cfg.getRamMb());
        m.put("serverName", cfg.getServerName());
        m.put("motd", cfg.getMotd());
        m.put("port", cfg.getPort());
        m.put("bedrockPort", cfg.effectiveBedrockPort());
        m.put("packHttpPort", cfg.getResourcePackHttpPort());
        m.put("dashboardPort", cfg.getWebDashboardPort());
        m.put("activePack", server.getResourcePacks().getActivePack()
                .map(p -> p.getFileName()).orElse("none"));
        m.put("javaClients", server.getGateway().getClients().countEdition(ClientEdition.JAVA));
        m.put("bedrockClients", server.getGateway().getClients().countEdition(ClientEdition.BEDROCK));
        m.put("statusText", server.statusReport());
        json(ex, 200, m);
    }

    private void apiConnect(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) || !requireAuth(ex)) {
            return;
        }
        var ep = server.publicEndpoint();
        ServerConfig cfg = server.getConfig();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("javaJoin", ep.javaJoinAddress());
        m.put("bedrockJoin", ep.bedrockJoinAddress());
        m.put("crossplayJoin", ep.crossplayJoinAddress());
        m.put("packUrl", ep.packBaseUrl());
        m.put("exposed", cfg.isInternetExposed());
        m.put("publicHost", ep.publicHost());
        m.put("localhost", "127.0.0.1:" + cfg.getPort());
        json(ex, 200, m);
    }

    private void apiConfig(HttpExchange ex) throws IOException {
        if (!requireAuth(ex)) {
            return;
        }
        ServerConfig cfg = server.getConfig();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("server-name", cfg.getServerName());
            m.put("motd", cfg.getMotd());
            m.put("bind-host", cfg.getBindHost());
            m.put("port", cfg.getPort());
            m.put("bedrock-port", cfg.getBedrockPort());
            m.put("max-players", cfg.getMaxPlayers());
            m.put("ram-mb", cfg.getRamMb());
            m.put("ram-min-mb", cfg.getRamMinMb());
            m.put("view-distance", cfg.getViewDistance());
            m.put("java-enabled", cfg.isJavaEnabled());
            m.put("bedrock-enabled", cfg.isBedrockEnabled());
            m.put("shared-listen-port", cfg.isSharedListenPort());
            m.put("crossplay-enabled", cfg.isCrossplayEnabled());
            m.put("allow-localhost", cfg.isAllowLocalhost());
            m.put("online-mode", cfg.isOnlineMode());
            m.put("resource-pack-enabled", cfg.isResourcePackEnabled());
            m.put("resource-pack-file", cfg.getResourcePackFile());
            m.put("internet-exposed", cfg.isInternetExposed());
            m.put("public-host", cfg.getPublicHost());
            m.put("server-domain", cfg.getServerDomain());
            m.put("public-port", cfg.getPublicPort());
            m.put("web-dashboard-port", cfg.getWebDashboardPort());
            json(ex, 200, m);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod()) || "PUT".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(readBody(ex));
            applyConfig(cfg, body);
            cfg.save();
            try {
                server.reloadLimitsFromConfig();
            } catch (Exception ignored) {
            }
            json(ex, 200, Map.of("ok", true));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private void applyConfig(ServerConfig cfg, Map<String, String> body) {
        body.forEach((k, v) -> {
            switch (k) {
                case "server-name" -> cfg.setServerName(v);
                case "motd" -> cfg.setMotd(v);
                case "bind-host" -> cfg.setBindHost(v);
                case "port" -> cfg.setPort(parseInt(v, cfg.getPort()));
                case "bedrock-port" -> cfg.setBedrockPort(parseInt(v, cfg.getBedrockPort()));
                case "max-players" -> cfg.setMaxPlayers(parseInt(v, cfg.getMaxPlayers()));
                case "ram-mb" -> cfg.setRamMb(parseInt(v, cfg.getRamMb()));
                case "ram-min-mb" -> cfg.setRamMinMb(parseInt(v, cfg.getRamMinMb()));
                case "view-distance" -> cfg.setViewDistance(parseInt(v, cfg.getViewDistance()));
                case "java-enabled" -> cfg.setJavaEnabled(bool(v));
                case "bedrock-enabled" -> cfg.setBedrockEnabled(bool(v));
                case "shared-listen-port" -> cfg.setSharedListenPort(bool(v));
                case "crossplay-enabled" -> cfg.setCrossplayEnabled(bool(v));
                case "allow-localhost" -> cfg.setAllowLocalhost(bool(v));
                case "online-mode" -> cfg.setOnlineMode(bool(v));
                case "resource-pack-enabled" -> cfg.setResourcePackEnabled(bool(v));
                case "resource-pack-file" -> cfg.setResourcePackFile(v);
                case "internet-exposed" -> cfg.setInternetExposed(bool(v));
                case "public-host" -> cfg.setPublicHost(v);
                case "server-domain" -> cfg.setServerDomain(v);
                case "public-port" -> cfg.setPublicPort(parseInt(v, 0));
                case "web-dashboard-port" -> cfg.setWebDashboardPort(parseInt(v, cfg.getWebDashboardPort()));
                default -> {
                    if (k != null && !k.isBlank()) {
                        cfg.set(k, v);
                    }
                }
            }
        });
    }

    private void apiStart(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod()) || !requireAuth(ex)) {
            return;
        }
        if (server.isRunning()) {
            json(ex, 200, Map.of("ok", true, "message", "already running"));
            return;
        }
        try {
            server.start();
            json(ex, 200, Map.of("ok", true, "message", "started"));
        } catch (Exception e) {
            json(ex, 500, Map.of("ok", false, "error", e.getMessage() == null ? "start failed" : e.getMessage()));
        }
    }

    private void apiStop(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod()) || !requireAuth(ex)) {
            return;
        }
        if (!server.isRunning()) {
            json(ex, 200, Map.of("ok", true, "message", "already stopped"));
            return;
        }
        server.stop();
        json(ex, 200, Map.of("ok", true, "message", "stopped"));
    }

    private void apiCommand(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod()) || !requireAuth(ex)) {
            return;
        }
        Map<String, String> body = TinyJson.parseFlatObject(readBody(ex));
        String cmd = body.getOrDefault("command", body.getOrDefault("cmd", "")).trim();
        if (cmd.isEmpty()) {
            json(ex, 400, Map.of("error", "missing command"));
            return;
        }
        String result = server.executeCommand(cmd);
        json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result));
    }

    private void apiPlugins(HttpExchange ex) throws IOException {
        if (!requireAuth(ex)) {
            return;
        }
        PluginManager pm = server.getPluginManager();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (var p : pm.listPlugins()) {
                list.add(Map.of(
                        "fileName", p.fileName(),
                        "sizeLabel", p.sizeLabel(),
                        "sizeBytes", p.sizeBytes()));
            }
            json(ex, 200, Map.of("plugins", list));
            return;
        }
        if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(readBody(ex));
            String name = body.getOrDefault("fileName", "");
            boolean ok = pm.removePlugin(name);
            json(ex, 200, Map.of("ok", ok));
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            // Install from a path already on the host (release/ops upload separately)
            Map<String, String> body = TinyJson.parseFlatObject(readBody(ex));
            String path = body.getOrDefault("path", "");
            if (path.isBlank()) {
                json(ex, 400, Map.of("error", "provide path to a .jar on the server"));
                return;
            }
            try {
                var info = pm.addPlugin(Path.of(path));
                json(ex, 200, Map.of("ok", true, "fileName", info.fileName()));
            } catch (Exception e) {
                json(ex, 400, Map.of("ok", false, "error", e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private void apiModules(HttpExchange ex) throws IOException {
        if (!requireAuth(ex)) {
            return;
        }
        ModuleManager mm = server.getModuleManager();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (var m : mm.listModules()) {
                list.add(Map.of(
                        "fileName", m.fileName(),
                        "sizeLabel", m.sizeLabel(),
                        "sizeBytes", m.sizeBytes()));
            }
            json(ex, 200, Map.of("modules", list));
            return;
        }
        if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(readBody(ex));
            boolean ok = mm.removeModule(body.getOrDefault("fileName", ""));
            json(ex, 200, Map.of("ok", ok));
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(readBody(ex));
            try {
                var info = mm.addModule(Path.of(body.getOrDefault("path", "")));
                json(ex, 200, Map.of("ok", true, "fileName", info.fileName()));
            } catch (Exception e) {
                json(ex, 400, Map.of("ok", false, "error", e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private void apiPacks(HttpExchange ex) throws IOException {
        if (!requireAuth(ex)) {
            return;
        }
        ResourcePackManager packs = server.getResourcePacks();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            List<Map<String, Object>> list = new ArrayList<>();
            String active = packs.getActivePack().map(p -> p.getFileName()).orElse("");
            for (var p : packs.listPacks()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fileName", p.getFileName());
                row.put("active", p.getFileName().equals(active));
                row.put("sizeLabel", p.sizeLabel());
                list.add(row);
            }
            json(ex, 200, Map.of("packs", list, "active", active));
            return;
        }
        Map<String, String> body = TinyJson.parseFlatObject(readBody(ex));
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            String action = body.getOrDefault("action", "setActive");
            try {
                if ("setActive".equals(action)) {
                    packs.setActivePack(body.getOrDefault("fileName", ""));
                } else if ("clear".equals(action)) {
                    packs.setActivePack("");
                } else if ("remove".equals(action)) {
                    packs.removePack(body.getOrDefault("fileName", ""));
                } else if ("add".equals(action)) {
                    packs.addPack(Path.of(body.getOrDefault("path", "")));
                }
                json(ex, 200, Map.of("ok", true));
            } catch (Exception e) {
                json(ex, 400, Map.of("ok", false, "error", e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private void apiConsole(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) || !requireAuth(ex)) {
            return;
        }
        json(ex, 200, Map.of("text", ConsoleBus.get().getRecentText()));
    }

    private void apiConsoleStream(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) || !requireAuth(ex)) {
            return;
        }
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "text/event-stream; charset=utf-8");
        h.set("Cache-Control", "no-cache");
        h.set("Connection", "keep-alive");
        ex.sendResponseHeaders(200, 0);
        OutputStream out = ex.getResponseBody();
        sseClients.add(out);
        // seed recent
        writeSse(out, ConsoleBus.get().getRecentText());
        try {
            // keep open until client disconnects
            while (true) {
                Thread.sleep(15_000);
                writeSse(out, ""); // keepalive comment via empty data
                out.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception e) {
            sseClients.remove(out);
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void apiVehicles(HttpExchange ex) throws IOException {
        if (!requireAuth(ex)) {
            return;
        }
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            json(ex, 200, Map.of(
                    "types", List.of(
                            "chassis", "buggy", "hoverbike", "truck_4x4", "monster_truck",
                            "sport_car", "hypercar", "lambo", "ferrari", "mclaren", "porsche"),
                    "hint", "POST {\"action\":\"spawn\",\"type\":\"lambo\"} or shop/upgrades commands"));
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(readBody(ex));
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
            json(ex, 200, Map.of("ok", true, "command", cmd, "result", result == null ? "" : result));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private void apiPregen(HttpExchange ex) throws IOException {
        if (!requireAuth(ex)) {
            return;
        }
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            // Status via console command (plugin owns job state)
            String result = server.executeCommand("yappregen status all");
            json(ex, 200, Map.of(
                    "ok", true,
                    "status", result == null ? "" : result,
                    "shapes", List.of("radius", "circle", "corners", "polygon", "worldborder", "selection"),
                    "hint", "POST {\"action\":\"start\",\"world\":\"world\",\"shape\":\"radius\",\"radius\":\"8\"}"));
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(readBody(ex));
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
                    json(ex, 400, Map.of("error", "unknown action"));
                    return;
                }
            }
            String result = server.executeCommand(cmd);
            json(ex, 200, Map.of("ok", true, "command", cmd, "result", result == null ? "" : result));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private void broadcastSse(String line) {
        if (line == null) {
            return;
        }
        List<OutputStream> dead = new ArrayList<>();
        for (OutputStream out : sseClients) {
            try {
                writeSse(out, line);
            } catch (IOException e) {
                dead.add(out);
            }
        }
        sseClients.removeAll(dead);
    }

    private static void writeSse(OutputStream out, String line) throws IOException {
        if (line == null || line.isEmpty()) {
            return;
        }
        for (String part : line.split("\n", -1)) {
            out.write(("data: " + part + "\n").getBytes(StandardCharsets.UTF_8));
        }
        out.write("\n".getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            in.transferTo(buf);
            return buf.toString(StandardCharsets.UTF_8);
        }
    }

    private static void json(HttpExchange ex, int code, Map<String, ?> body) throws IOException {
        byte[] bytes = TinyJson.obj(body).getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void text(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static int parseInt(String v, int fallback) {
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean bool(String v) {
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }
}

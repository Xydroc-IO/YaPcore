package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.client.ClientEdition;
import com.yapcore.config.ServerConfig;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardLinkSnapshot;
import com.yapcore.web.PluginCompatMatrix;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;
import com.yapcore.web.metrics.ChassisMetricsHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardStatusApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;
    private final ChassisMetricsHandler metricsHandler;

    public DashboardStatusApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
        this.metricsHandler = new ChassisMetricsHandler(server);
    }

    public void apiStatus(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) || !auth.requireAuth(ex)) {
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
        m.put("ticks", server.getEngine().gameCore().getTickCounter());
        m.put("linkProcessRunning", server.getLinkProcess().isRunning());
        m.put("pid", ProcessHandle.current().pid());
        m.put("statusText", server.statusReport());
        m.put("networkHealth", buildNetworkHealth(cfg));
        m.put("observability", metricsHandler.statusSnippet());
        DashboardHttp.json(ex, 200, m);
    }

    public void apiConnect(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) || !auth.requireAuth(ex)) {
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
        DashboardHttp.json(ex, 200, m);
    }

    public void apiConfig(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
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
            m.put("yap-ranks-auto-apply", cfg.isYapRanksAutoApply());
            m.put("ops", cfg.getOps());
            m.put("auto-op", cfg.isAutoOp());
            DashboardHttp.json(ex, 200, m);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod()) || "PUT".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            applyConfig(cfg, body);
            cfg.save();
            try {
                server.reloadLimitsFromConfig();
            } catch (Exception ignored) {
            }
            DashboardHttp.json(ex, 200, Map.of("ok", true));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiStart(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod()) || !auth.requireAuth(ex)) {
            return;
        }
        if (server.isRunning()) {
            DashboardHttp.json(ex, 200, Map.of("ok", true, "message", "already running"));
            return;
        }
        try {
            server.start();
            DashboardHttp.json(ex, 200, Map.of("ok", true, "message", "started"));
        } catch (Exception e) {
            DashboardHttp.json(ex, 500, Map.of("ok", false, "error", e.getMessage() == null ? "start failed" : e.getMessage()));
        }
    }

    public void apiStop(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod()) || !auth.requireAuth(ex)) {
            return;
        }
        if (!server.isRunning()) {
            DashboardHttp.json(ex, 200, Map.of("ok", true, "message", "already stopped"));
            return;
        }
        server.stop();
        DashboardHttp.json(ex, 200, Map.of("ok", true, "message", "stopped"));
    }

    public void apiCommand(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod()) || !auth.requireAuth(ex)) {
            return;
        }
        Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
        String cmd = body.getOrDefault("command", body.getOrDefault("cmd", "")).trim();
        if (cmd.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "missing command"));
            return;
        }
        String result = server.executeCommand(cmd);
        DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result));
    }

    private Map<String, Object> buildNetworkHealth(ServerConfig cfg) {
        Map<String, Object> h = new LinkedHashMap<>();
        Path root = server.getRootDir();
        h.put("foliaRunning", server.isRunning());
        h.put("bedrockEnabled", cfg.isBedrockEnabled());
        h.put("crossplayEnabled", cfg.isCrossplayEnabled());
        h.put("velocityEnabled", cfg.isVelocityEnabled());
        h.put("linkEmbed", cfg.isLinkEmbed());
        var link = DashboardLinkSnapshot.snapshot(
                root, cfg.getLinkEmbedHome(), cfg.isLinkEmbed(), cfg.isVelocityEnabled());
        h.put("linkProcessRunning", server.getLinkProcess().isRunning());
        h.put("linkConfigPresent", link.get("configPresent"));
        h.put("linkSuiteComplete", link.get("suiteComplete"));
        h.put("linkServers", link.get("servers"));
        List<String> pluginNames = server.getPluginManager().listPlugins().stream()
                .map(p -> p.fileName()).toList();
        h.put("pluginCount", pluginNames.size());
        h.put("compatWarnings", PluginCompatMatrix.warningsForInstalled(pluginNames).size());
        h.put("lastBedrockPlaySmoke", smokeArtifactTime(root.resolve("build/bedrock-play-smoke-latest.json")));
        h.put("lastNetworkSmoke", smokeArtifactTime(root.resolve("build/smoke-network-full-latest.json")));
        h.put("summary", networkHealthSummary(h));
        return h;
    }

    private static String networkHealthSummary(Map<String, Object> h) {
        int warnings = (int) h.getOrDefault("compatWarnings", 0);
        boolean running = Boolean.TRUE.equals(h.get("foliaRunning"));
        if (!running) {
            return "Server stopped";
        }
        if (warnings > 0) {
            return "Running — " + warnings + " plugin compat warning(s)";
        }
        return "Running — network stack OK";
    }

    private static List<String> parseOpsList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String smokeArtifactTime(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return "never";
            }
            return Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()).toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void applyConfig(ServerConfig cfg, Map<String, String> body) {
        body.forEach((k, v) -> {
            switch (k) {
                case "server-name" -> cfg.setServerName(v);
                case "motd" -> cfg.setMotd(v);
                case "bind-host" -> cfg.setBindHost(v);
                case "port" -> cfg.setPort(DashboardHttp.parseInt(v, cfg.getPort()));
                case "bedrock-port" -> cfg.setBedrockPort(DashboardHttp.parseInt(v, cfg.getBedrockPort()));
                case "max-players" -> cfg.setMaxPlayers(DashboardHttp.parseInt(v, cfg.getMaxPlayers()));
                case "ram-mb" -> cfg.setRamMb(DashboardHttp.parseInt(v, cfg.getRamMb()));
                case "ram-min-mb" -> cfg.setRamMinMb(DashboardHttp.parseInt(v, cfg.getRamMinMb()));
                case "view-distance" -> cfg.setViewDistance(DashboardHttp.parseInt(v, cfg.getViewDistance()));
                case "java-enabled" -> cfg.setJavaEnabled(DashboardHttp.bool(v));
                case "bedrock-enabled" -> cfg.setBedrockEnabled(DashboardHttp.bool(v));
                case "shared-listen-port" -> cfg.setSharedListenPort(DashboardHttp.bool(v));
                case "crossplay-enabled" -> cfg.setCrossplayEnabled(DashboardHttp.bool(v));
                case "allow-localhost" -> cfg.setAllowLocalhost(DashboardHttp.bool(v));
                case "online-mode" -> cfg.setOnlineMode(DashboardHttp.bool(v));
                case "resource-pack-enabled" -> cfg.setResourcePackEnabled(DashboardHttp.bool(v));
                case "resource-pack-file" -> cfg.setResourcePackFile(v);
                case "internet-exposed" -> cfg.setInternetExposed(DashboardHttp.bool(v));
                case "public-host" -> cfg.setPublicHost(v);
                case "server-domain" -> cfg.setServerDomain(v);
                case "public-port" -> cfg.setPublicPort(DashboardHttp.parseInt(v, 0));
                case "web-dashboard-port" -> cfg.setWebDashboardPort(DashboardHttp.parseInt(v, cfg.getWebDashboardPort()));
                case "auto-op" -> cfg.setAutoOp(DashboardHttp.bool(v));
                case "ops" -> cfg.setOps(parseOpsList(v));
                default -> {
                    if (k != null && !k.isBlank()) {
                        cfg.set(k, v);
                    }
                }
            }
        });
    }
}

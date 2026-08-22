package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.config.ServerConfig;
import com.yapcore.network.publicity.PublicEndpoint;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardLinkSnapshot;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Admin setup + monitoring — network access, nginx, dashboard self-config, diagnostics. */
public final class DashboardAdminApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardAdminApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiAdmin(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        ServerConfig cfg = server.getConfig();
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            DashboardHttp.json(ex, 200, snapshot(cfg, root));
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            switch (action) {
                case "save-access" -> {
                    applyAccess(cfg, body);
                    cfg.save();
                    server.getResourcePacks().setPublicHost(new PublicEndpoint(cfg).publicHost());
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true, "action", action, "snapshot", snapshot(cfg, root)));
                }
                case "save-nginx" -> {
                    applyNginx(cfg, body);
                    cfg.save();
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                }
                case "save-dashboard" -> {
                    applyDashboard(cfg, body);
                    cfg.save();
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true, "action", action,
                            "note", "Dashboard bind/port changes apply on next YaPcore restart."));
                }
                case "save-proxy" -> {
                    applyProxy(cfg, body);
                    cfg.save();
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                }
                case "rotate-token" -> {
                    byte[] raw = new byte[16];
                    new SecureRandom().nextBytes(raw);
                    String token = HexFormat.of().formatHex(raw);
                    cfg.setWebDashboardToken(token);
                    cfg.save();
                    auth.setToken(token);
                    DashboardHttp.json(ex, 200, Map.of(
                            "ok", true, "action", action, "token", token,
                            "note", "New token saved — update your login."));
                }
                case "nginx-dry-run" -> runScript(ex, root, "scripts/nginx-setup.sh", "--dry-run", 120);
                case "run-smoke" -> runScript(ex, root, "scripts/smoke-network-full.sh", "", 600);
                case "crashdump" -> {
                    String result = server.executeCommand("crashdump");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action, "result", result == null ? "" : result));
                }
                default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private Map<String, Object> snapshot(ServerConfig cfg, Path root) {
        PublicEndpoint ep = new PublicEndpoint(cfg);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("running", server.isRunning());
        runtime.put("ticks", server.getEngine().gameCore().getTickCounter());
        runtime.put("linkProcessRunning", server.getLinkProcess().isRunning());
        runtime.put("pid", ProcessHandle.current().pid());
        runtime.put("rootDir", root.toString());
        runtime.put("gameAuthority", cfg.getGameAuthority().name().toLowerCase());
        out.put("runtime", runtime);

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("enabled", cfg.isWebDashboardEnabled());
        dashboard.put("port", cfg.getWebDashboardPort());
        dashboard.put("bind", cfg.getWebDashboardBind());
        dashboard.put("localhostOnly", cfg.isWebDashboardLocalhostOnly());
        dashboard.put("tokenMasked", maskToken(auth.getToken()));
        out.put("dashboard", dashboard);

        Map<String, Object> access = new LinkedHashMap<>();
        access.put("internetExposed", cfg.isInternetExposed());
        access.put("serverDomain", cfg.getServerDomain());
        access.put("publicHost", cfg.getPublicHost());
        access.put("publicPort", cfg.getPublicPort());
        access.put("publicBedrockPort", cfg.getPublicBedrockPort());
        access.put("publicPackPort", cfg.getPublicPackPort());
        access.put("srvEnabled", cfg.isSrvEnabled());
        access.put("srvExample", ep.srvRecordExample());
        access.put("javaJoin", ep.javaJoinAddress());
        access.put("bedrockJoin", ep.bedrockJoinAddress());
        out.put("access", access);

        Map<String, Object> nginx = new LinkedHashMap<>();
        nginx.put("allowLocalhost", cfg.isAllowLocalhost());
        nginx.put("nginxPublicPort", cfg.getNginxPublicPort());
        nginx.put("nginxPackPort", cfg.getNginxPackPort());
        nginx.put("nginxDomain", cfg.getNginxDomain());
        out.put("nginx", nginx);

        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("velocityEnabled", cfg.isVelocityEnabled());
        proxy.put("velocityOnlineMode", cfg.isVelocityOnlineMode());
        proxy.put("velocityBindLocalhost", cfg.isVelocityBindLocalhost());
        proxy.put("velocitySecretFile", cfg.getVelocitySecretFile());
        proxy.put("linkEmbed", cfg.isLinkEmbed());
        proxy.put("linkEmbedHome", cfg.getLinkEmbedHome());
        out.put("proxy", proxy);

        Map<String, Object> smoke = new LinkedHashMap<>();
        smoke.put("lastNetworkSmoke", artifactTime(root.resolve("build/smoke-network-full-latest.json")));
        smoke.put("lastBedrockPlaySmoke", artifactTime(root.resolve("build/bedrock-play-smoke-latest.json")));
        out.put("smoke", smoke);

        var link = DashboardLinkSnapshot.snapshot(
                root, cfg.getLinkEmbedHome(), cfg.isLinkEmbed(), cfg.isVelocityEnabled());
        out.put("linkSuiteComplete", link.get("suiteComplete"));
        out.put("linkServers", link.get("servers"));

        out.put("hint", "POST save-access | save-nginx | save-dashboard | save-proxy | rotate-token | nginx-dry-run | run-smoke | crashdump");
        return out;
    }

    private static void applyAccess(ServerConfig cfg, Map<String, String> body) {
        if (body.containsKey("internetExposed")) {
            cfg.setInternetExposed(DashboardHttp.bool(body.get("internetExposed")));
        }
        if (body.containsKey("serverDomain")) {
            cfg.setServerDomain(body.get("serverDomain"));
        }
        if (body.containsKey("publicHost")) {
            cfg.setPublicHost(body.get("publicHost"));
        }
        if (body.containsKey("publicPort")) {
            cfg.setPublicPort(DashboardHttp.parseInt(body.get("publicPort"), cfg.getPublicPort()));
        }
        if (body.containsKey("publicBedrockPort")) {
            cfg.setPublicBedrockPort(DashboardHttp.parseInt(body.get("publicBedrockPort"), cfg.getPublicBedrockPort()));
        }
        if (body.containsKey("publicPackPort")) {
            cfg.setPublicPackPort(DashboardHttp.parseInt(body.get("publicPackPort"), cfg.getPublicPackPort()));
        }
        if (body.containsKey("srvEnabled")) {
            cfg.setSrvEnabled(DashboardHttp.bool(body.get("srvEnabled")));
        }
        PublicEndpoint ep = new PublicEndpoint(cfg);
        if (cfg.isInternetExposed()) {
            ep.applyInternetBind();
        }
        String advertise = firstNonBlank(cfg.getPublicHost(), cfg.getServerDomain());
        if (advertise != null && cfg.getResourcePackPublicHost().isBlank()) {
            cfg.setResourcePackPublicHost(advertise);
        }
    }

    private static void applyNginx(ServerConfig cfg, Map<String, String> body) {
        if (body.containsKey("allowLocalhost")) {
            cfg.setAllowLocalhost(DashboardHttp.bool(body.get("allowLocalhost")));
        }
        if (body.containsKey("nginxPublicPort")) {
            cfg.setNginxPublicPort(DashboardHttp.parseInt(body.get("nginxPublicPort"), cfg.getNginxPublicPort()));
        }
        if (body.containsKey("nginxPackPort")) {
            cfg.setNginxPackPort(DashboardHttp.parseInt(body.get("nginxPackPort"), cfg.getNginxPackPort()));
        }
        if (body.containsKey("nginxDomain")) {
            cfg.setNginxDomain(body.get("nginxDomain"));
        }
        if (DashboardHttp.bool(body.getOrDefault("allowLocalhost", Boolean.toString(cfg.isAllowLocalhost())))) {
            cfg.setBindHost("0.0.0.0");
        }
    }

    private static void applyDashboard(ServerConfig cfg, Map<String, String> body) {
        if (body.containsKey("enabled")) {
            cfg.setWebDashboardEnabled(DashboardHttp.bool(body.get("enabled")));
        }
        if (body.containsKey("port")) {
            cfg.setWebDashboardPort(DashboardHttp.parseInt(body.get("port"), cfg.getWebDashboardPort()));
        }
        if (body.containsKey("bind")) {
            cfg.setWebDashboardBind(body.get("bind"));
        }
        if (body.containsKey("localhostOnly")) {
            cfg.setWebDashboardLocalhostOnly(DashboardHttp.bool(body.get("localhostOnly")));
        }
    }

    private static void applyProxy(ServerConfig cfg, Map<String, String> body) {
        if (body.containsKey("velocityEnabled")) {
            cfg.setVelocityEnabled(DashboardHttp.bool(body.get("velocityEnabled")));
        }
        if (body.containsKey("velocityOnlineMode")) {
            cfg.setVelocityOnlineMode(DashboardHttp.bool(body.get("velocityOnlineMode")));
        }
        if (body.containsKey("velocityBindLocalhost")) {
            cfg.setVelocityBindLocalhost(DashboardHttp.bool(body.get("velocityBindLocalhost")));
        }
        if (body.containsKey("velocitySecretFile")) {
            cfg.setVelocitySecretFile(body.get("velocitySecretFile"));
        }
        if (body.containsKey("linkEmbed")) {
            cfg.setLinkEmbed(DashboardHttp.bool(body.get("linkEmbed")));
        }
        if (body.containsKey("linkEmbedHome")) {
            cfg.setLinkEmbedHome(body.get("linkEmbedHome"));
        }
    }

    private void runScript(HttpExchange ex, Path root, String scriptPath, String extraArg, int timeoutSec)
            throws IOException {
        Path script = root.resolve(scriptPath);
        if (!Files.isRegularFile(script)) {
            DashboardHttp.json(ex, 404, Map.of("error", "missing " + scriptPath));
            return;
        }
        ProcessBuilder pb = new ProcessBuilder("bash", script.toString());
        if (extraArg != null && !extraArg.isBlank()) {
            pb.command().add(extraArg.trim());
        }
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(timeoutSec, TimeUnit.SECONDS);
            DashboardHttp.json(ex, 200, Map.of(
                    "ok", true,
                    "exit", p.exitValue(),
                    "output", out,
                    "script", scriptPath));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            DashboardHttp.json(ex, 500, Map.of("error", "interrupted"));
        }
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "…" + token.substring(token.length() - 4);
    }

    private static String artifactTime(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return "never";
            }
            return Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()).toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }
}

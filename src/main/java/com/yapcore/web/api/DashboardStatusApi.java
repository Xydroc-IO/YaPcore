package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.client.ClientEdition;
import com.yapcore.config.BedrockModeApplier;
import com.yapcore.config.ServerConfig;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardLinkSnapshot;
import com.yapcore.web.DashboardNetworkSnapshots;
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
        try {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long max = rt.maxMemory() / (1024 * 1024);
            ServerConfig cfg = server.getConfig();
            Map<String, Object> m = new LinkedHashMap<>();
            boolean chassisRunning = server.isRunning();
            boolean fleetOn = cfg.isFleetEnabled();
            boolean primaryRunning = fleetOn && server.fleet().isPrimaryRunning();
            int fleetUp = 0;
            int fleetTotal = 0;
            if (fleetOn) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> instances = (List<Map<String, Object>>)
                            server.fleet().statusSnapshot().get("instances");
                    if (instances != null) {
                        fleetTotal = instances.size();
                        for (Map<String, Object> i : instances) {
                            if (Boolean.TRUE.equals(i.get("running"))
                                    || "RUNNING".equalsIgnoreCase(String.valueOf(i.get("state")))) {
                                fleetUp++;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // keep zeros
                }
            }
            // Game network is "up" when chassis DualStack is on, or fleet Folia JVMs are up.
            boolean gameUp = chassisRunning || primaryRunning || fleetUp > 0;
            m.put("running", gameUp);
            m.put("chassisRunning", chassisRunning);
            m.put("fleetEnabled", fleetOn);
            m.put("fleetPrimaryRunning", primaryRunning);
            m.put("fleetRunningCount", fleetUp);
            m.put("fleetInstanceCount", fleetTotal);
            m.put("runLabel", fleetOn
                    ? (fleetUp > 0
                    ? ("FLEET · " + fleetUp + "/" + fleetTotal + " up")
                    : "FLEET · stopped")
                    : (chassisRunning ? "RUNNING" : "STOPPED"));
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
            try {
                m.put("bedrockFeel", buildBedrockFeel(cfg));
            } catch (Exception e) {
                m.put("bedrockFeel", Map.of("error", String.valueOf(e.getMessage())));
            }
            try {
                m.put("networkHealth", buildNetworkHealth(cfg));
            } catch (Exception e) {
                m.put("networkHealth", Map.of("error", String.valueOf(e.getMessage())));
            }
            try {
                m.put("observability", metricsHandler.statusSnippet());
            } catch (Exception e) {
                m.put("observability", Map.of("error", String.valueOf(e.getMessage())));
            }
            DashboardHttp.json(ex, 200, m);
        } catch (Exception e) {
            DashboardHttp.json(ex, 500, Map.of(
                    "ok", false,
                    "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
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
            m.put("bedrock-mode", cfg.getBedrockMode());
            // Chassis Folia UDP bind (always false in native — Link owns Bedrock).
            m.put("bedrock-enabled", cfg.isBedrockEnabled());
            // What operators mean by "Bedrock on": Link edge (or Geyser) accepts BE clients.
            m.put("allow-bedrock-players", DashboardLinkSnapshot.allowBedrockPlayers(
                    server.getRootDir(), cfg.getLinkEmbedHome()));
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
            m.put("parity.bedrock-feel", cfg.isParityBedrockFeel());
            m.put("parity.bedrock-band", cfg.getParityBedrockBand());
            m.put("ops", cfg.getOps());
            m.put("auto-op", cfg.isAutoOp());
            DashboardHttp.json(ex, 200, m);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod()) || "PUT".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String requestedMode = body.containsKey("bedrock-mode")
                    ? body.get("bedrock-mode")
                    : body.get("bedrockMode");
            boolean modeChanging = requestedMode != null
                    && !BedrockModeApplier.normalize(requestedMode)
                            .equals(BedrockModeApplier.normalize(cfg.getBedrockMode()));
            if (modeChanging) {
                if (server.isRunning() || server.getLinkProcess().isRunning()
                        || server.getGeyserProcess().isRunning()) {
                    DashboardHttp.json(ex, 409, Map.of(
                            "ok", false,
                            "error", "Stop the game stack before changing Bedrock path."));
                    return;
                }
                Path linkDir = DashboardLinkSnapshot.resolveHome(
                        server.getRootDir(), server.getConfig().getLinkEmbedHome());
                Map<String, Object> applied = BedrockModeApplier.apply(
                        server.getRootDir(), linkDir, cfg.getFile(), requestedMode);
                cfg.load();
                // Mode applier owns these derived flags; do not let the form override them.
                body.remove("bedrock-mode");
                body.remove("bedrockMode");
                body.remove("bedrock-enabled");
                body.remove("crossplay-enabled");
                body.remove("shared-listen-port");
                if (!body.isEmpty()) {
                    applyConfig(cfg, body);
                    cfg.save();
                }
                try {
                    server.reloadLimitsFromConfig();
                } catch (Exception ignored) {
                }
                DashboardHttp.json(ex, 200, Map.of(
                        "ok", true,
                        "bedrockMode", applied.get("bedrockMode"),
                        "note", "Bedrock path updated on Link + chassis."));
                return;
            }
            // Unchanged bedrock-mode is always posted by the form — ignore it so routine
            // saves (MOTD, RAM, etc.) work while Folia/Link are running.
            body.remove("bedrock-mode");
            body.remove("bedrockMode");
            String allowBe = body.remove("allow-bedrock-players");
            if (allowBe == null) {
                allowBe = body.remove("allowBedrockPlayers");
            }
            applyConfig(cfg, body);
            // Keep Folia Velocity online-mode aligned with the shared product auth setting.
            if (cfg.isVelocityEnabled()) {
                cfg.setVelocityOnlineMode(cfg.isOnlineMode());
            }
            if (allowBe != null) {
                boolean want = DashboardHttp.bool(allowBe);
                DashboardLinkSnapshot.setAllowBedrockPlayers(
                        server.getRootDir(), cfg.getLinkEmbedHome(), want);
                if (want) {
                    cfg.setCrossplayEnabled(true);
                }
                // Never leave chassis Folia binding Bedrock UDP in native/geyser-backup.
                if (!"forwarder".equals(cfg.getBedrockMode())) {
                    cfg.setBedrockEnabled(false);
                    cfg.setSharedListenPort(false);
                }
            }
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
        try {
            ServerConfig cfg = server.getConfig();
            if (cfg.isFleetEnabled()) {
                if (!server.isRunning()) {
                    server.start();
                } else if (!server.fleet().isPrimaryRunning()) {
                    String primary = server.fleet().store().primaryId();
                    if (primary == null || primary.isBlank()) {
                        primary = "lobby";
                    }
                    server.fleet().startInstance(primary);
                }
                DashboardHttp.json(ex, 200, Map.of("ok", true, "message", "fleet start",
                        "fleetEnabled", true));
                return;
            }
            if (server.isRunning()) {
                DashboardHttp.json(ex, 200, Map.of("ok", true, "message", "already running"));
                return;
            }
            server.start();
            DashboardHttp.json(ex, 200, Map.of("ok", true, "message", "started"));
        } catch (Exception e) {
            DashboardHttp.json(ex, 500, Map.of("ok", false,
                    "error", e.getMessage() == null ? "start failed" : e.getMessage()));
        }
    }

    public void apiStop(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod()) || !auth.requireAuth(ex)) {
            return;
        }
        try {
            ServerConfig cfg = server.getConfig();
            if (cfg.isFleetEnabled()) {
                server.fleet().stopAllLocal();
                if (server.isRunning()) {
                    server.stop();
                }
                DashboardHttp.json(ex, 200, Map.of("ok", true, "message", "fleet stopped",
                        "fleetEnabled", true));
                return;
            }
            if (!server.isRunning()) {
                DashboardHttp.json(ex, 200, Map.of("ok", true, "message", "already stopped"));
                return;
            }
            server.stop();
            DashboardHttp.json(ex, 200, Map.of("ok", true, "message", "stopped"));
        } catch (Exception e) {
            DashboardHttp.json(ex, 500, Map.of("ok", false,
                    "error", e.getMessage() == null ? "stop failed" : e.getMessage()));
        }
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
        String instanceId = body.getOrDefault("instanceId", "").trim();
        try {
            String result;
            if (!instanceId.isEmpty() && server.getConfig().isFleetEnabled()) {
                result = server.fleet().dispatch(instanceId, cmd);
            } else {
                result = server.executeCommand(cmd);
            }
            DashboardHttp.json(ex, 200, Map.of(
                    "ok", true,
                    "result", result == null ? "" : result,
                    "instanceId", instanceId.isEmpty() ? "primary" : instanceId));
        } catch (Exception e) {
            DashboardHttp.json(ex, 400, Map.of("ok", false, "error", String.valueOf(e.getMessage())));
        }
    }

    private Map<String, Object> buildBedrockFeel(ServerConfig cfg) {
        Map<String, Object> m = new LinkedHashMap<>();
        String band = cfg.getParityBedrockBand();
        m.put("enabled", cfg.isParityBedrockFeel());
        m.put("band", band);
        Path root = server.getRootDir();
        Path manifest = root.resolve(
                "src/main/resources/protocol/bedrock/parity/" + band + "/provenance/manifest.v1.json");
        Path releasePin = root.resolve("parity-" + band + "-provenance.manifest.v1.json");
        m.put("provenancePresent", Files.isRegularFile(manifest) || Files.isRegularFile(releasePin));
        m.put("provenancePath", "protocol/bedrock/parity/" + band + "/provenance/manifest.v1.json");
        Path clientMods = root.resolve("dist/client-mods/client_mods.zip");
        m.put("clientModsPresent", Files.isRegularFile(clientMods));
        m.put("clientModsPath", "dist/client-mods/client_mods.zip");
        Path pack = root.resolve("resourcepacks/yap-bedrock-blocks/EXTRACT_REPORT.json");
        m.put("blockPackExtractOk", Files.isRegularFile(pack));
        m.put("matrixDoc", "docs/product/BEDROCK_FEEL_MATRIX.md");
        m.put("smokeScript", "scripts/parity/smoke-bedrock-feel.sh");
        m.put("requiredJeMods", List.of("yap-presence", "yap-blocks"));
        m.put("summary", cfg.isParityBedrockFeel()
                ? ("Parity ON · band " + band + " · JE needs yap-presence HELLO")
                : ("Parity OFF · band " + band + " (catalogs still load)"));
        return m;
    }

    private Map<String, Object> buildNetworkHealth(ServerConfig cfg) {
        Map<String, Object> h = new LinkedHashMap<>();
        Path root = server.getRootDir();
        var link = DashboardLinkSnapshot.snapshot(
                root, cfg.getLinkEmbedHome(), cfg.isLinkEmbed(), cfg.isVelocityEnabled());
        h.put("foliaRunning", cfg.isFleetEnabled()
                ? server.fleet().isPrimaryRunning()
                : server.isRunning());
        boolean linkBedrock = Boolean.TRUE.equals(link.get("bedrockEnabled"));
        String mode = String.valueOf(link.getOrDefault("bedrockMode", cfg.getBedrockMode()));
        boolean geyserBackup = "geyser-backup".equals(mode) || Boolean.TRUE.equals(link.get("geyserEnabled"));
        // Effective Bedrock for players: Link edge (native/forwarder) or Geyser — not chassis UDP alone.
        h.put("bedrockEnabled", cfg.isCrossplayEnabled()
                && (cfg.isBedrockEnabled() || linkBedrock || geyserBackup
                || "native".equals(mode) || "forwarder".equals(mode) || "first-party".equals(mode)));
        h.put("crossplayEnabled", cfg.isCrossplayEnabled());
        h.put("bedrockMode", mode);
        h.put("velocityEnabled", cfg.isVelocityEnabled());
        h.put("linkEmbed", cfg.isLinkEmbed());
        h.put("linkProcessRunning", server.getLinkProcess().isRunning());
        h.put("linkConfigPresent", link.get("configPresent"));
        h.put("linkSuiteComplete", link.get("suiteComplete"));
        h.put("linkServers", link.get("servers"));
        h.put("backendHealth", com.yapcore.fleet.link.BackendHealthBridge.collectMaps(
                root, cfg.getLinkEmbedHome(), server.getLinkProcess().isRunning()));
        List<String> pluginNames = server.getPluginManager().listPlugins().stream()
                .map(p -> p.fileName()).toList();
        h.put("pluginCount", pluginNames.size());
        h.put("compatWarnings", PluginCompatMatrix.warningsForInstalled(pluginNames).size());
        h.put("lastBedrockPlaySmoke", smokeArtifactTime(root.resolve("build/bedrock-play-smoke-latest.json")));
        h.put("lastNetworkSmoke", smokeArtifactTime(root.resolve("build/smoke-network-full-latest.json")));
        h.put("summary", networkHealthSummary(h));
        h.put("opsPlugins", DashboardNetworkSnapshots.opsPhase8Summary(root));
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
                case "bedrock-mode" -> cfg.setBedrockMode(v);
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
                case "yap-ranks-auto-apply" -> cfg.setYapRanksAutoApply(DashboardHttp.bool(v));
                case "parity.bedrock-feel", "parityBedrockFeel" -> cfg.setParityBedrockFeel(DashboardHttp.bool(v));
                case "parity.bedrock-band", "parityBedrockBand" -> cfg.setParityBedrockBand(v);
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

package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.fleet.model.FleetNode;
import com.yapcore.fleet.service.FleetInstanceOps;
import com.yapcore.fleet.service.FleetService;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard routes: {@code /api/fleet} — registry, instances, nodes, bootstrap, deploy. */
public final class DashboardFleetApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;
    private final DashboardFleetConsoleApi consoleApi;

    public DashboardFleetApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
        this.consoleApi = new DashboardFleetConsoleApi(server, auth);
    }

    public DashboardFleetConsoleApi consoleApi() {
        return consoleApi;
    }

    public void apiFleet(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        FleetService fleet = server.fleet();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            DashboardHttp.json(ex, 200, fleet.statusSnapshot());
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            DashboardHttp.json(ex, 405, Map.of("error", "method not allowed"));
            return;
        }
        Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
        String action = body.getOrDefault("action", "").toLowerCase();
        try {
            DashboardHttp.json(ex, 200, dispatchAction(fleet, action, body));
        } catch (Exception e) {
            DashboardHttp.json(ex, 400, Map.of("ok", false, "error", String.valueOf(e.getMessage())));
        }
    }

    private Map<String, Object> dispatchAction(
            FleetService fleet, String action, Map<String, String> body) throws Exception {
        return switch (action) {
            case "enable-fleet" -> fleet.enableFleet();
            case "create" -> fleet.createInstance(
                    body.getOrDefault("id", ""),
                    body.get("displayName"),
                    parseIntOrNull(body.get("port")),
                    !"false".equalsIgnoreCase(body.getOrDefault("autoStart", "false")),
                    parseIntOrNull(first(body, "ramMb", "ram-mb")),
                    parseIntOrNull(first(body, "ramMinMb", "ram-min-mb")));
            case "start" -> {
                fleet.startInstance(required(body, "id"));
                yield Map.of("ok", true, "action", "start", "id", body.get("id"));
            }
            case "stop" -> {
                fleet.stopInstance(required(body, "id"));
                yield Map.of("ok", true, "action", "stop", "id", body.get("id"));
            }
            case "restart" -> {
                fleet.restartInstance(required(body, "id"));
                yield Map.of("ok", true, "action", "restart", "id", body.get("id"));
            }
            case "get-settings" -> fleet.readInstanceSettings(required(body, "id"));
            case "update-settings" -> fleet.writeInstanceSettings(required(body, "id"), body);
            case "list-plugins" -> fleet.listInstancePlugins(required(body, "id"));
            case "install-plugin" -> fleet.installCatalogJar(
                    required(body, "jar"),
                    FleetInstanceOps.parseIds(body.getOrDefault("instanceIds", body.get("id"))),
                    body.getOrDefault("restartPolicy", "none"));
            case "install-core-network", "seed-defaults" ->
                    fleet.installCoreNetworkDefaults(required(body, "id"));
            case "enable-plugin" -> fleet.setInstancePluginEnabled(
                    required(body, "id"), required(body, "jar"), true);
            case "disable-plugin" -> fleet.setInstancePluginEnabled(
                    required(body, "id"), required(body, "jar"), false);
            case "uninstall-plugin" -> fleet.uninstallInstancePlugin(
                    required(body, "id"), required(body, "jar"));
            case "delete" -> fleet.deleteInstance(required(body, "id"));
            case "set-auto-start" -> fleet.setAutoStart(
                    required(body, "id"),
                    !"false".equalsIgnoreCase(body.getOrDefault("autoStart", "true")));
            case "sync-link" -> fleet.syncLink();
            case "command" -> {
                String result = fleet.dispatch(required(body, "id"), body.getOrDefault("command", ""));
                yield Map.of("ok", true, "action", "command", "result", result);
            }
            case "put-node" -> fleet.putNode(new FleetNode(
                    required(body, "id"),
                    required(body, "baseUrl"),
                    body.getOrDefault("tokenRef", ""),
                    body.getOrDefault("displayName", "")));
            case "remove-node" -> fleet.removeNode(required(body, "id"));
            case "probe-node" -> {
                Map<String, Object> health = fleet.probeNode(required(body, "id"));
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("action", "probe-node");
                out.put("health", health);
                yield out;
            }
            case "deploy" -> {
                List<String> ids = Arrays.stream(body.getOrDefault("instanceIds", "")
                                .split("[,\\s]+"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                Path source = Path.of(required(body, "sourcePath"));
                if (!source.isAbsolute()) {
                    source = server.getRootDir().resolve(source);
                }
                yield fleet.deploy(
                        ids, source, body.get("target"), body.getOrDefault("restartPolicy", "none"));
            }
            case "write-node-token" -> {
                String nid = required(body, "id");
                String token = required(body, "token");
                Path tokenFile = server.getRootDir().resolve("fleet/agents/" + nid + ".token");
                java.nio.file.Files.createDirectories(tokenFile.getParent());
                java.nio.file.Files.writeString(tokenFile, token.trim() + "\n");
                yield fleet.putNode(new FleetNode(
                        nid,
                        required(body, "baseUrl"),
                        "fleet/agents/" + nid + ".token",
                        body.getOrDefault("displayName", "")));
            }
            case "bootstrap" -> fleet.bootstrap(
                    body.get("jdbcUrl"),
                    !"false".equalsIgnoreCase(body.getOrDefault("createSurvival", "true")),
                    !"false".equalsIgnoreCase(body.getOrDefault("enableVelocity", "true")),
                    "true".equalsIgnoreCase(body.getOrDefault("startInstances", "false")));
            default -> Map.of("ok", false, "error", "unknown action: " + action);
        };
    }

    private static String required(Map<String, String> body, String key) throws IOException {
        String v = body.get(key);
        if (v == null || v.isBlank()) {
            throw new IOException("missing " + key);
        }
        return v.trim();
    }

    private static Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String first(Map<String, String> body, String... keys) {
        for (String key : keys) {
            String v = body.get(key);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}

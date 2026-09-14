package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.fleet.service.FleetInstanceOps;
import com.yapcore.fleet.service.FleetService;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Instance-scoped fleet routes under {@code /api/fleet/instances/...} and catalog install.
 */
public final class DashboardFleetInstanceApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardFleetInstanceApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    /** Prefix handler for {@code /api/fleet/instances}. */
    public void apiInstances(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        FleetService fleet = server.fleet();
        String path = ex.getRequestURI().getPath();
        // /api/fleet/instances/{id}/settings|plugins
        String rest = path.startsWith("/api/fleet/instances/")
                ? path.substring("/api/fleet/instances/".length())
                : "";
        if (rest.isBlank() || rest.equals("/")) {
            DashboardHttp.json(ex, 404, Map.of("error", "instance id required"));
            return;
        }
        String[] parts = rest.split("/");
        String id = parts[0];
        String resource = parts.length > 1 ? parts[1] : "";
        try {
            if ("settings".equals(resource)) {
                handleSettings(ex, fleet, id);
                return;
            }
            if ("plugins".equals(resource)) {
                handlePlugins(ex, fleet, id);
                return;
            }
            DashboardHttp.json(ex, 404, Map.of("error", "unknown resource"));
        } catch (Exception e) {
            DashboardHttp.json(ex, 400, Map.of("ok", false, "error", String.valueOf(e.getMessage())));
        }
    }

    /** {@code POST /api/plugins/install} — catalog jar → one or many instances. */
    public void apiPluginsInstall(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            DashboardHttp.json(ex, 405, Map.of("error", "method not allowed"));
            return;
        }
        Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
        try {
            List<String> ids = FleetInstanceOps.parseIds(body.getOrDefault("instances",
                    body.getOrDefault("instanceIds", "")));
            Map<String, Object> out = server.fleet().installCatalogJar(
                    required(body, "jar"),
                    ids,
                    body.getOrDefault("restartPolicy", "none"));
            DashboardHttp.json(ex, 200, out);
        } catch (Exception e) {
            DashboardHttp.json(ex, 400, Map.of("ok", false, "error", String.valueOf(e.getMessage())));
        }
    }

    private void handleSettings(HttpExchange ex, FleetService fleet, String id) throws Exception {
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            DashboardHttp.json(ex, 200, fleet.readInstanceSettings(id));
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            DashboardHttp.json(ex, 200, fleet.writeInstanceSettings(id, body));
            return;
        }
        DashboardHttp.json(ex, 405, Map.of("error", "method not allowed"));
    }

    private void handlePlugins(HttpExchange ex, FleetService fleet, String id) throws Exception {
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            DashboardHttp.json(ex, 200, fleet.listInstancePlugins(id));
            return;
        }
        Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
        String action = body.getOrDefault("action", "").toLowerCase();
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> out = switch (action) {
                case "enable" -> fleet.setInstancePluginEnabled(id, required(body, "jar"), true);
                case "disable" -> fleet.setInstancePluginEnabled(id, required(body, "jar"), false);
                case "install" -> {
                    Map<String, Object> r = fleet.installCatalogJar(
                            required(body, "jar"),
                            List.of(id),
                            body.getOrDefault("restartPolicy", "none"));
                    yield r;
                }
                case "install-core-network", "seed-defaults" -> fleet.installCoreNetworkDefaults(id);
                case "uninstall", "delete" -> fleet.uninstallInstancePlugin(id, required(body, "jar"));
                default -> {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("ok", false);
                    err.put("error", "unknown action: " + action);
                    yield err;
                }
            };
            DashboardHttp.json(ex, 200, out);
            return;
        }
        if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
            DashboardHttp.json(ex, 200, fleet.uninstallInstancePlugin(id, required(body, "jar")));
            return;
        }
        DashboardHttp.json(ex, 405, Map.of("error", "method not allowed"));
    }

    private static String required(Map<String, String> body, String key) throws IOException {
        String v = body.get(key);
        if (v == null || v.isBlank()) {
            throw new IOException("missing " + key);
        }
        return v.trim();
    }
}

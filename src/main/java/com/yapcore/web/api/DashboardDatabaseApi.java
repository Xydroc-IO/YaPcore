package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.fleet.local.InstanceLayout;
import com.yapcore.fleet.ops.DatabaseSetup;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dashboard routes: {@code /api/database} — engine pick, Docker start, JDBC ensure, fleet sync. */
public final class DashboardDatabaseApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardDatabaseApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiDatabase(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            DashboardHttp.json(ex, 200, DatabaseSetup.status(server.getRootDir()));
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            DashboardHttp.json(ex, 405, Map.of("error", "method not allowed"));
            return;
        }
        Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
        String action = body.getOrDefault("action", "ensure").toLowerCase();
        try {
            DashboardHttp.json(ex, 200, dispatch(action, body));
        } catch (Exception e) {
            DashboardHttp.json(ex, 400, Map.of(
                    "ok", false,
                    "error", e.getMessage() == null ? "database action failed" : e.getMessage()));
        }
    }

    private Map<String, Object> dispatch(String action, Map<String, String> body) throws Exception {
        var root = server.getRootDir();
        DatabaseSetup.Engine engine = DatabaseSetup.Engine.parse(
                body.getOrDefault("engine", body.getOrDefault("jdbcUrl", "mysql")));
        return switch (action) {
            case "status" -> DatabaseSetup.status(root);
            case "ensure", "setup", "configure" -> DatabaseSetup.ensure(
                    root,
                    engine,
                    body.getOrDefault("serverId", "lobby"),
                    body.get("host"),
                    !"false".equalsIgnoreCase(body.getOrDefault("syncFleet", "true")));
            case "start-docker", "start" -> DatabaseSetup.startDocker(root, engine);
            case "stop-docker", "stop" -> DatabaseSetup.stopDocker(root, engine);
            case "sync-fleet", "sync-catalog" -> {
                int n = InstanceLayout.syncSharedCatalogDataToLocalFleet(root);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("action", "sync-fleet");
                out.put("filesWritten", n);
                yield out;
            }
            default -> Map.of("ok", false, "error", "unknown action: " + action
                    + " (ensure | start-docker | stop-docker | sync-fleet | status)");
        };
    }
}

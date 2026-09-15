package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.setup.SetupActions;
import com.yapcore.setup.SetupChecklist;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** First-boot / ops setup checklist for Linux + Windows. */
public final class DashboardSetupApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardSetupApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiSetup(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(SetupChecklist.snapshot(root));
            snap.put("hint", "POST action=accept-eula|seed-defaults|link-forwarding|fetch-tebex|"
                    + "enable-grim|disable-grim|fetch-grim|production-profile|nginx-dry-run|build-folia");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").trim();
            if (action.isEmpty()) {
                DashboardHttp.json(ex, 400, Map.of("error", "action required"));
                return;
            }
            try {
                Map<String, Object> result = new LinkedHashMap<>(SetupActions.run(root, action, body));
                result.put("checklist", SetupChecklist.snapshot(root));
                int status = Boolean.FALSE.equals(result.get("ok")) ? 400 : 200;
                if (result.containsKey("error") && !Boolean.TRUE.equals(result.get("ok"))) {
                    status = result.get("error").toString().startsWith("missing") ? 404 : 400;
                }
                DashboardHttp.json(ex, status, result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                DashboardHttp.json(ex, 500, Map.of("error", "interrupted"));
            } catch (Exception e) {
                DashboardHttp.json(ex, 500, Map.of(
                        "error", e.getMessage() == null ? "setup failed" : e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }
}

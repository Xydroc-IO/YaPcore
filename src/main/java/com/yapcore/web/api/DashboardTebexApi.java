package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardNetworkSnapshotWriters;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.DashboardTebexKitGrants;
import com.yapcore.web.DashboardTebexRecipes;
import com.yapcore.web.DashboardTebexStatus;
import com.yapcore.web.DashboardTebexWriters;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dashboard routes for third-party Tebex Folia store plugin. */
final class DashboardTebexApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    DashboardTebexApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    void handle(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.tebex(root));
            snap.put("ok", true);
            snap.put("hint", "POST set-secret | save-settings | save-webhook | save-recipes | upsert-recipe"
                    + " | delete-recipe | cancel-grant | reload | info | forcecheck");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            switch (action) {
                case "set-secret", "secret" -> setSecret(ex, root, body);
                case "save-settings" -> saveSettings(ex, root, body);
                case "save-webhook" -> saveWebhook(ex, root, body);
                case "save-recipes" -> saveRecipes(ex, root, body);
                case "upsert-recipe" -> upsertRecipe(ex, root, body);
                case "delete-recipe" -> deleteRecipe(ex, root, body);
                case "cancel-grant" -> cancelGrant(ex, root, body);
                case "reload" -> {
                    String result = server.executeCommand("tebex reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result));
                }
                case "info" -> info(ex);
                case "forcecheck", "force-check" -> forceCheck(ex);
                default -> DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private void setSecret(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        String secret = body.getOrDefault("secret", body.getOrDefault("key", "")).trim();
        if (secret.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "secret required"));
            return;
        }
        if (secret.contains(" ") || secret.contains("\"") || secret.contains("'")
                || secret.contains("\n") || secret.contains("\r")) {
            DashboardHttp.json(ex, 400, Map.of("error", "secret must be a single token (no spaces/quotes)"));
            return;
        }
        DashboardTebexWriters.saveSecret(root, secret);
        String result = server.executeCommand("tebex secret " + secret);
        Map<String, Object> parsed = DashboardTebexStatus.rememberInfo(result);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("action", "set-secret");
        resp.put("secretConfigured", true);
        resp.put("secretMasked", DashboardNetworkSnapshots.maskSecret(secret));
        resp.put("result", result == null ? "" : result);
        resp.put("status", parsed);
        DashboardHttp.json(ex, 200, resp);
    }

    private void saveSettings(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        DashboardTebexWriters.saveSettings(root, body);
        String result = server.executeCommand("tebex reload");
        DashboardHttp.json(ex, 200, Map.of(
                "ok", true,
                "action", "save-settings",
                "result", result == null ? "" : result));
    }

    private void saveWebhook(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        Boolean enabled = body.containsKey("webhookEnabled")
                ? !"false".equalsIgnoreCase(body.get("webhookEnabled"))
                : (body.containsKey("enabled") ? !"false".equalsIgnoreCase(body.get("enabled")) : null);
        Integer port = null;
        if (body.containsKey("webhookPort")) {
            port = Integer.parseInt(body.get("webhookPort").trim());
        } else if (body.containsKey("port")) {
            port = Integer.parseInt(body.get("port").trim());
        }
        String secret = body.get("webhookSecret");
        if (secret == null) {
            secret = body.get("secret");
        }
        Boolean enforceIps = null;
        if (body.containsKey("webhookEnforceIps")) {
            enforceIps = !"false".equalsIgnoreCase(body.get("webhookEnforceIps"));
        } else if (body.containsKey("enforceIps")) {
            enforceIps = !"false".equalsIgnoreCase(body.get("enforceIps"));
        }
        DashboardNetworkSnapshotWriters.saveTebexWebhook(root, enabled, port, secret, enforceIps);
        String result = server.executeCommand("yaptebex reload");
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("action", "save-webhook");
        resp.put("result", result == null ? "" : result);
        resp.putAll(DashboardNetworkSnapshots.tebex(root));
        DashboardHttp.json(ex, 200, resp);
    }

    private void saveRecipes(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        try {
            String yaml = body.getOrDefault("yaml", body.getOrDefault("text", ""));
            DashboardTebexRecipes.saveYamlText(root, yaml);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("action", "save-recipes");
            resp.put("packageRecipes", DashboardTebexRecipes.load(root));
            resp.put("recipesYaml", DashboardTebexRecipes.loadYamlText(root));
            DashboardHttp.json(ex, 200, resp);
        } catch (IllegalArgumentException e) {
            DashboardHttp.json(ex, 400, Map.of("error", e.getMessage()));
        }
    }

    private void upsertRecipe(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        try {
            DashboardTebexRecipes.upsertRecipe(root,
                    body.getOrDefault("name", ""),
                    body.getOrDefault("commands", ""));
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("action", "upsert-recipe");
            resp.put("packageRecipes", DashboardTebexRecipes.load(root));
            resp.put("recipesYaml", DashboardTebexRecipes.loadYamlText(root));
            DashboardHttp.json(ex, 200, resp);
        } catch (IllegalArgumentException e) {
            DashboardHttp.json(ex, 400, Map.of("error", e.getMessage()));
        }
    }

    private void deleteRecipe(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        try {
            DashboardTebexRecipes.deleteRecipe(root, body.getOrDefault("name", ""));
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("action", "delete-recipe");
            resp.put("packageRecipes", DashboardTebexRecipes.load(root));
            resp.put("recipesYaml", DashboardTebexRecipes.loadYamlText(root));
            DashboardHttp.json(ex, 200, resp);
        } catch (IllegalArgumentException e) {
            DashboardHttp.json(ex, 400, Map.of("error", e.getMessage()));
        }
    }

    private void cancelGrant(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        try {
            long id = Long.parseLong(body.getOrDefault("id", "0").trim());
            boolean removed = DashboardTebexKitGrants.cancelGrant(root, id);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", removed);
            resp.put("action", "cancel-grant");
            resp.put("id", id);
            resp.putAll(DashboardTebexKitGrants.snapshot(root));
            if (!removed) {
                resp.put("error", "grant not found or already delivered");
            }
            DashboardHttp.json(ex, removed ? 200 : 404, resp);
        } catch (NumberFormatException e) {
            DashboardHttp.json(ex, 400, Map.of("error", "numeric grant id required"));
        } catch (Exception e) {
            DashboardHttp.json(ex, 500, Map.of(
                    "error", e.getMessage() == null ? "cancel failed" : e.getMessage()));
        }
    }

    private void info(HttpExchange ex) throws IOException {
        String result = server.executeCommand("tebex info");
        Map<String, Object> status = DashboardTebexStatus.rememberInfo(result);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("action", "info");
        resp.put("result", result == null ? "" : result);
        resp.put("status", status);
        DashboardHttp.json(ex, 200, resp);
    }

    private void forceCheck(HttpExchange ex) throws IOException {
        String result = server.executeCommand("tebex forcecheck");
        Map<String, Object> check = DashboardTebexStatus.rememberForceCheck(result);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("action", "forcecheck");
        resp.put("result", result == null ? "" : result);
        resp.put("status", check);
        DashboardHttp.json(ex, 200, resp);
    }
}

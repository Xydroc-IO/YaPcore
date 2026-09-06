package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.DashboardNetworkSnapshotWriters;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard routes: map, guard, lagguard. */
final class DashboardGameplayMapGuardApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    DashboardGameplayMapGuardApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    void apiMap(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.map(root));
            snap.put("ok", true);
            snap.put("hint", "POST reload | render");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            if ("save-settings".equals(action)) {
                try {
                    Integer interval = body.containsKey("renderIntervalMinutes")
                            ? Integer.parseInt(body.get("renderIntervalMinutes")) : null;
                    List<String> worlds = null;
                    if (body.containsKey("worlds")) {
                        worlds = DashboardApiUtil.splitLines(body.get("worlds").replace(",", "\n"));
                    }
                    Boolean markersPlayers = body.containsKey("markersPlayers")
                            ? !"false".equalsIgnoreCase(body.get("markersPlayers")) : null;
                    Boolean markersNpcs = body.containsKey("markersNpcs")
                            ? !"false".equalsIgnoreCase(body.get("markersNpcs")) : null;
                    Boolean markersRegions = body.containsKey("markersRegions")
                            ? !"false".equalsIgnoreCase(body.get("markersRegions")) : null;
                    Integer markersPoll = null;
                    if (body.containsKey("markersPollSeconds")) {
                        markersPoll = Integer.parseInt(body.get("markersPollSeconds"));
                    }
                    DashboardNetworkSnapshotWriters.saveMapSettings(root, interval, worlds,
                            markersPlayers, markersNpcs, markersRegions, markersPoll);
                    server.executeCommand("yapmap reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                } catch (Exception e) {
                    DashboardHttp.json(ex, 500, Map.of("error", e.getMessage()));
                }
                return;
            }
            String cmd = switch (action) {
                case "reload" -> "yapmap reload";
                case "render" -> "yapmap render";
                default -> null;
            };
            if (cmd == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                return;
            }
            String result = server.executeCommand(cmd);
            DashboardHttp.json(ex, 200, Map.of("ok", true, "command", cmd, "result", result == null ? "" : result));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    void apiGuard(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.guard(root));
            String status = server.executeCommand("yapguard status");
            snap.put("ok", true);
            snap.put("status", status == null ? "" : status);
            snap.put("hint", "POST reload | alerts-on | alerts-off");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            if ("save-settings".equals(action)) {
                try {
                    DashboardNetworkSnapshotWriters.saveGuardSettings(root,
                            body.containsKey("flyEnabled") ? !"false".equalsIgnoreCase(body.get("flyEnabled")) : null,
                            body.containsKey("speedEnabled") ? !"false".equalsIgnoreCase(body.get("speedEnabled")) : null,
                            body.containsKey("reachEnabled") ? !"false".equalsIgnoreCase(body.get("reachEnabled")) : null,
                            body.containsKey("scaffoldEnabled") ? !"false".equalsIgnoreCase(body.get("scaffoldEnabled")) : null,
                            body.containsKey("maxViolations") ? Integer.parseInt(body.get("maxViolations")) : null,
                            body.containsKey("decaySeconds") ? Integer.parseInt(body.get("decaySeconds")) : null,
                            body.containsKey("alertsEnabled") ? !"false".equalsIgnoreCase(body.get("alertsEnabled")) : null);
                    server.executeCommand("yapguard reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                } catch (Exception e) {
                    DashboardHttp.json(ex, 500, Map.of("error", e.getMessage()));
                }
                return;
            }
            String cmd = switch (action) {
                case "reload" -> "yapguard reload";
                case "alerts-on" -> "yapguard alerts on";
                case "alerts-off" -> "yapguard alerts off";
                case "player-status" -> "yapguard status " + body.getOrDefault("player", "");
                default -> null;
            };
            if (cmd == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                return;
            }
            String result = server.executeCommand(cmd);
            DashboardHttp.json(ex, 200, Map.of("ok", true, "command", cmd, "result", result == null ? "" : result));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    void apiLagGuard(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>(DashboardNetworkSnapshots.lagguard(root));
            String status = server.executeCommand("yaplagguard status");
            snap.put("ok", true);
            snap.put("status", status == null ? "" : status);
            snap.put("hint", "POST reload | save-settings");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            if ("save-settings".equals(action)) {
                try {
                    DashboardNetworkSnapshotWriters.saveLagGuardSettings(root,
                            body.containsKey("enabled") ? !"false".equalsIgnoreCase(body.get("enabled")) : null,
                            body.containsKey("maxEntitiesPerChunk") ? Integer.parseInt(body.get("maxEntitiesPerChunk")) : null,
                            body.containsKey("maxPrimedTntPerChunk") ? Integer.parseInt(body.get("maxPrimedTntPerChunk")) : null,
                            body.containsKey("maxHopperTransfersPerWindow")
                                    ? Integer.parseInt(body.get("maxHopperTransfersPerWindow")) : null,
                            body.containsKey("hopperWindowTicks") ? Integer.parseInt(body.get("hopperWindowTicks")) : null,
                            body.containsKey("maxRedstoneEventsPerWindow")
                                    ? Integer.parseInt(body.get("maxRedstoneEventsPerWindow")) : null,
                            body.containsKey("redstoneWindowTicks") ? Integer.parseInt(body.get("redstoneWindowTicks")) : null,
                            body.containsKey("logTrips") ? !"false".equalsIgnoreCase(body.get("logTrips")) : null);
                    server.executeCommand("yaplagguard reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "action", action));
                } catch (Exception e) {
                    DashboardHttp.json(ex, 500, Map.of("error", e.getMessage() == null ? "save failed" : e.getMessage()));
                }
                return;
            }
            if ("reload".equals(action)) {
                String result = server.executeCommand("yaplagguard reload");
                DashboardHttp.json(ex, 200, Map.of("ok", true, "command", "yaplagguard reload",
                        "result", result == null ? "" : result));
                return;
            }
            DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }
}

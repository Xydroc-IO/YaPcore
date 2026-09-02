package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.DashboardPlayerList;
import com.yapcore.web.DashboardSeenPlayers;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Online players, moderation, teleport, and quick rank actions for admins. */
public final class DashboardPlayersApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardPlayersApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiPlayers(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>();
            List<Map<String, Object>> online = DashboardPlayerList.onlinePlayers(server);
            List<Map<String, Object>> seen = DashboardSeenPlayers.load(root, online);
            snap.put("ok", true);
            snap.put("running", server.isRunning());
            snap.put("online", online);
            snap.put("seen", seen);
            snap.put("seenCount", seen.size());
            snap.put("count", online.size());
            snap.put("maxPlayers", server.getMaxPlayers());
            snap.put("spawn", DashboardNetworkSnapshots.essentialsSpawn(root));
            snap.put("moderation", DashboardNetworkSnapshots.moderation(root));
            snap.put("groups", DashboardNetworkSnapshots.perms(root).get("groups"));
            snap.put("durationHelp", "1h, 30m, 7d, 2w — for temp ban / mute / timeout");
            snap.put("hint", "Ban/kick/mute by username, UUID, or last IP. Economy: give/take/set/reset balance. Everyone who has joined is listed below.");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            String player = firstNonBlank(body.get("player"), body.get("target"), body.get("name"));
            String reason = body.getOrDefault("reason", "Staff action via admin dashboard").trim();
            String duration = body.getOrDefault("duration", "1d").trim();
            String cmd = buildCommand(action, body, player, reason, duration,
                    DashboardNetworkSnapshots.essentialsSpawn(root));
            if (cmd == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "unknown action or missing fields: " + action));
                return;
            }
            String result = server.executeCommand(cmd);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("action", action);
            resp.put("command", cmd);
            resp.put("result", result == null ? "" : result);
            List<Map<String, Object>> online = DashboardPlayerList.onlinePlayers(server);
            resp.put("online", online);
            resp.put("seen", DashboardSeenPlayers.load(root, online));
            DashboardHttp.json(ex, 200, resp);
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private static String buildCommand(
            String action,
            Map<String, String> body,
            String player,
            String reason,
            String duration,
            Map<String, Object> spawn
    ) {
        return switch (action) {
            case "kick" -> require(player, "kick " + q(player) + " " + q(reason));
            case "ban" -> require(player, "ban " + q(player) + " " + q(reason));
            case "tempban", "timeout" -> require(player, "tempban " + q(player) + " " + q(duration) + " " + q(reason));
            case "unban" -> require(player, "unban " + q(player));
            case "ipban" -> {
                String ip = firstNonBlank(body.get("ip"), player);
                yield require(ip, "ipban " + q(ip) + " " + q(reason));
            }
            case "unbanip" -> {
                String ip = firstNonBlank(body.get("ip"), player);
                yield require(ip, "unbanip " + q(ip));
            }
            case "mute" -> require(player, "mute " + q(player) + " " + q(reason));
            case "tempmute" -> require(player, "tempmute " + q(player) + " " + q(duration) + " " + q(reason));
            case "unmute" -> require(player, "unmute " + q(player));
            case "warn" -> require(player, "warn " + q(player) + " " + q(reason));
            case "history" -> require(player, "history " + q(player));
            case "check", "modcheck" -> require(player, "modcheck " + q(player));
            case "banlist" -> "banlist " + body.getOrDefault("limit", "25");
            case "seen-refresh", "seen-snapshot" -> "yapmod seen snapshot";
            case "tp" -> {
                String x = body.get("x");
                String y = body.get("y");
                String z = body.get("z");
                if (player != null && x != null && y != null && z != null) {
                    yield "tp " + q(player) + " " + x.trim() + " " + y.trim() + " " + z.trim();
                }
                yield null;
            }
            case "tp-to" -> {
                String dest = body.get("destination");
                if (player != null && dest != null && !dest.isBlank()) {
                    yield "tp " + q(player) + " " + q(dest.trim());
                }
                yield null;
            }
            case "tp-spawn" -> {
                if (player == null) {
                    yield null;
                }
                yield "tp " + q(player) + " " + spawn.get("x") + " " + spawn.get("y") + " " + spawn.get("z");
            }
            case "set-group", "set-rank" -> {
                String group = body.getOrDefault("group", "default").trim().toLowerCase();
                yield require(player, "yapperm user " + q(player) + " parent set " + q(group));
            }
            case "promote" -> require(player, "promote " + q(player));
            case "demote" -> require(player, "demote " + q(player));
            case "user-info", "perm-info" -> require(player, "yapperm user " + q(player) + " info");
            case "bal", "balance" -> require(player, "bal " + q(player));
            case "eco-give", "give-money", "givemoney" -> ecoCmd("give", player, body.get("amount"));
            case "eco-take", "take-money" -> ecoCmd("take", player, body.get("amount"));
            case "eco-set", "set-balance", "set-money" -> ecoCmd("set", player, body.get("amount"));
            case "eco-reset", "reset-balance" -> require(player, "eco reset " + q(player));
            default -> null;
        };
    }

    private static String ecoCmd(String op, String player, String amountRaw) {
        if (player == null || player.isBlank()) {
            return null;
        }
        if (amountRaw == null || amountRaw.isBlank()) {
            return null;
        }
        String amount = amountRaw.trim();
        try {
            double value = Double.parseDouble(amount);
            if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return "eco " + op + " " + q(player) + " " + amount;
    }

    private static String require(String value, String cmd) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return cmd;
    }

    private static String q(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (raw.contains(" ")) {
            return "\"" + raw.replace("\"", "\\\"") + "\"";
        }
        return raw;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}

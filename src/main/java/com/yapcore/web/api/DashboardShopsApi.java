package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardChestShopUtil;
import com.yapcore.web.DashboardNpcUtil;
import com.yapcore.web.DashboardShopUtil;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dashboard bridge for YaPNpcs shop catalogs (offers / presets / prices). */
public final class DashboardShopsApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardShopsApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiShops(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>();
            List<Map<String, Object>> npcs = DashboardNpcUtil.parseListJson(server.executeCommand("npc list json"));
            List<Map<String, Object>> shops = new ArrayList<>();
            for (Map<String, Object> n : npcs) {
                String action = String.valueOf(n.getOrDefault("action", ""));
                Long catalogId = parseShopId(action);
                if (catalogId == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("npcId", n.get("id"));
                row.put("displayName", n.get("displayName"));
                row.put("world", n.get("world"));
                row.put("catalogId", catalogId);
                row.put("action", action);
                shops.add(row);
            }
            List<String> presets = DashboardShopUtil.parsePresetsJson(
                    server.executeCommand("npc shop presets json"));
            if (presets.isEmpty()) {
                presets = List.of("weapons", "armor", "tools", "food", "blocks", "redstone", "crafting",
                        "enchants", "farming", "fishing");
            }
            snap.put("ok", true);
            snap.put("shops", shops);
            snap.put("shopCount", shops.size());
            snap.put("presets", presets);
            snap.put("npcCount", npcs.size());
            List<Map<String, Object>> chest = DashboardChestShopUtil.parseList(
                    gameCommand("", "shop list json all"));
            snap.put("chestShops", chest);
            snap.put("chestCount", chest.size());
            snap.put("instances", instanceIds());
            snap.put("root", root.toString());
            snap.put("hint", "POST list | setitem | chest-list | chest-create | chest-set | chest-remove | chest-info");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase(Locale.ROOT);
            if (action.startsWith("chest-") || "chestlist".equals(action)) {
                handleChest(ex, action, body);
                return;
            }
            String cmd = shopCommand(action, body);
            if (cmd == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "unknown action or missing fields"));
                return;
            }
            String result = server.executeCommand(cmd);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("command", cmd);
            resp.put("result", result == null ? "" : result);
            if ("list".equals(action)) {
                resp.put("shop", DashboardShopUtil.parseShopJson(result));
            }
            if (List.of("enable", "clear", "apply", "addbuy", "addsell", "setitem", "deloffer",
                    "setoffer", "clearoffers").contains(action)) {
                List<Map<String, Object>> npcs = DashboardNpcUtil.parseListJson(
                        server.executeCommand("npc list json"));
                List<Map<String, Object>> shops = new ArrayList<>();
                for (Map<String, Object> n : npcs) {
                    Long catalogId = parseShopId(String.valueOf(n.getOrDefault("action", "")));
                    if (catalogId == null) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("npcId", n.get("id"));
                    row.put("displayName", n.get("displayName"));
                    row.put("catalogId", catalogId);
                    shops.add(row);
                }
                resp.put("shops", shops);
                String id = body.getOrDefault("id", body.getOrDefault("npcId", "")).trim();
                if (!id.isEmpty()) {
                    resp.put("shop", DashboardShopUtil.parseShopJson(
                            server.executeCommand("npc shop list " + id + " json")));
                }
            }
            DashboardHttp.json(ex, 200, resp);
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private static String shopCommand(String action, Map<String, String> body) {
        String id = body.getOrDefault("id", body.getOrDefault("npcId", "")).trim();
        return switch (action) {
            case "list" -> id.isEmpty() ? null : "npc shop list " + id + " json";
            case "presets" -> "npc shop presets json";
            case "enable" -> {
                if (id.isEmpty()) {
                    yield null;
                }
                String name = body.getOrDefault("name", body.getOrDefault("catalogName", "")).trim();
                yield name.isEmpty() ? "npc shop enable " + id : "npc shop enable " + id + " " + name;
            }
            case "clear" -> id.isEmpty() ? null : "npc shop clear " + id;
            case "clearoffers" -> id.isEmpty() ? null : "npc shop clearoffers " + id;
            case "apply" -> {
                if (id.isEmpty()) {
                    yield null;
                }
                String preset = body.getOrDefault("preset", "").trim().toLowerCase(Locale.ROOT);
                if (preset.isEmpty()) {
                    yield null;
                }
                boolean replace = "true".equalsIgnoreCase(body.getOrDefault("replace", "true"))
                        || "1".equals(body.getOrDefault("replace", ""))
                        || "--replace".equalsIgnoreCase(body.getOrDefault("replace", ""));
                yield "npc shop apply " + preset + " " + id + (replace ? " --replace" : "");
            }
            case "setitem", "item" -> {
                if (id.isEmpty()) {
                    yield null;
                }
                String material = body.getOrDefault("material", "").trim().toUpperCase(Locale.ROOT);
                String amount = body.getOrDefault("amount", "1").trim();
                String buy = priceOrDash(body.getOrDefault("buyPrice",
                        body.getOrDefault("buy", body.getOrDefault("priceBuy", ""))));
                String sell = priceOrDash(body.getOrDefault("sellPrice",
                        body.getOrDefault("sell", body.getOrDefault("priceSell", ""))));
                String stock = body.getOrDefault("stock", "-1").trim();
                if (material.isEmpty()) {
                    yield null;
                }
                yield "npc shop setitem " + id + " " + material + " " + amount + " " + buy + " " + sell + " " + stock;
            }
            case "addbuy", "addsell" -> {
                if (id.isEmpty()) {
                    yield null;
                }
                String material = body.getOrDefault("material", "").trim().toUpperCase(Locale.ROOT);
                String amount = body.getOrDefault("amount", "1").trim();
                String price = body.getOrDefault("price", "").trim();
                String stock = body.getOrDefault("stock", "-1").trim();
                if (material.isEmpty() || price.isEmpty()) {
                    yield null;
                }
                String op = "addbuy".equals(action) ? "addbuy" : "addsell";
                yield "npc shop " + op + " " + id + " " + material + " " + amount + " " + price + " " + stock;
            }
            case "setoffer", "setprice" -> {
                String offerId = body.getOrDefault("offerId", body.getOrDefault("oid", "")).trim();
                String price = body.getOrDefault("price", "").trim();
                if (offerId.isEmpty() || price.isEmpty()) {
                    yield null;
                }
                String amount = body.getOrDefault("amount", "-1").trim();
                String stock = body.getOrDefault("stock", "-2").trim();
                yield "npc shop setoffer " + offerId + " " + price + " " + amount + " " + stock;
            }
            case "deloffer" -> {
                String offerId = body.getOrDefault("offerId", body.getOrDefault("oid", "")).trim();
                if (id.isEmpty() || offerId.isEmpty()) {
                    yield null;
                }
                yield "npc shop deloffer " + id + " " + offerId;
            }
            default -> null;
        };
    }

    /** Blank / off / none → {@code -} for setitem disable side. */
    private static String priceOrDash(String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        String t = raw.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if ("-".equals(lower) || "off".equals(lower) || "none".equals(lower) || "null".equals(lower)) {
            return "-";
        }
        return t;
    }

    private static Long parseShopId(String actionRaw) {
        if (actionRaw == null || actionRaw.isBlank() || "null".equals(actionRaw)) {
            return null;
        }
        for (String part : actionRaw.split(";")) {
            String t = part.trim();
            int colon = t.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String type = t.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            if (!"shop".equals(type) && !"trader".equals(type)) {
                continue;
            }
            try {
                return Long.parseLong(t.substring(colon + 1).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void handleChest(HttpExchange ex, String action, Map<String, String> body) throws IOException {
        String instance = body.getOrDefault("instance", body.getOrDefault("serverId", "")).trim();
        String cmd = chestCommand(action, body);
        if (cmd == null) {
            DashboardHttp.json(ex, 400, Map.of("error", "unknown chest action or missing fields"));
            return;
        }
        String result = gameCommand(instance, cmd);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("command", cmd);
        resp.put("result", result == null ? "" : result);
        resp.put("chestShops", DashboardChestShopUtil.parseList(gameCommand(instance, "shop list json all")));
        if ("chest-info".equals(action) || "chestinfo".equals(action)) {
            resp.put("chest", DashboardChestShopUtil.parseOne(result));
        }
        DashboardHttp.json(ex, 200, resp);
    }

    private static String chestCommand(String action, Map<String, String> body) {
        String world = body.getOrDefault("world", "world").trim();
        String x = body.getOrDefault("x", "").trim();
        String y = body.getOrDefault("y", "").trim();
        String z = body.getOrDefault("z", "").trim();
        return switch (action) {
            case "chest-list", "chestlist" -> "shop list json all";
            case "chest-create", "chest-set", "chestcreate", "chestset" -> {
                String material = body.getOrDefault("material", "").trim().toUpperCase(Locale.ROOT);
                String amount = body.getOrDefault("amount", "1").trim();
                String price = body.getOrDefault("price", "").trim();
                if (world.isEmpty() || x.isEmpty() || y.isEmpty() || z.isEmpty()
                        || material.isEmpty() || price.isEmpty()) {
                    yield null;
                }
                String owner = body.getOrDefault("owner", "").trim();
                String op = "chest-set".equals(action) || "chestset".equals(action) ? "set" : "create";
                String cmd = "shop " + op + " " + world + " " + x + " " + y + " " + z
                        + " " + material + " " + amount + " " + price;
                yield owner.isEmpty() ? cmd : cmd + " " + owner;
            }
            case "chest-remove", "chestremove" -> {
                if (world.isEmpty() || x.isEmpty() || y.isEmpty() || z.isEmpty()) {
                    yield null;
                }
                yield "shop remove " + world + " " + x + " " + y + " " + z;
            }
            case "chest-info", "chestinfo" -> {
                if (world.isEmpty() || x.isEmpty() || y.isEmpty() || z.isEmpty()) {
                    yield null;
                }
                yield "shop info " + world + " " + x + " " + y + " " + z + " json";
            }
            default -> null;
        };
    }

    private String gameCommand(String instanceId, String cmd) {
        if (instanceId != null && !instanceId.isBlank() && instanceId.matches("[A-Za-z0-9_-]{1,32}")
                && server.fleet() != null && server.getConfig().isFleetEnabled()) {
            try {
                return server.fleet().dispatch(instanceId, cmd);
            } catch (Exception e) {
                return "dispatch failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
        return server.executeCommand(cmd);
    }

    private List<String> instanceIds() {
        if (server.fleet() == null || !server.getConfig().isFleetEnabled()) {
            return List.of();
        }
        return server.fleet().store().instances().stream().map(i -> i.id()).toList();
    }
}

package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.config.ServerConfig;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardPermsSnapshotWriters;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.PermissionCatalog;
import com.yapcore.web.PluginPermissionScanner;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Operators, default rank, and permission group management. */
public final class DashboardAccessApi {

    private static final char META_SEP = '\u001E';

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardAccessApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiAccess(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        ServerConfig cfg = server.getConfig();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("ok", true);
            snap.put("ops", cfg.getOps());
            snap.put("autoOp", cfg.isAutoOp());
            snap.put("onlineMode", cfg.isOnlineMode());
            snap.putAll(DashboardNetworkSnapshots.perms(root));
            snap.put("catalog", catalogWithDiscovered(root));
            snap.put("templates", PermissionCatalog.templateSummaries());
            snap.put("hint", "POST save-group-nodes | user-perm | group-perm | user-perm-unset | "
                    + "group-perm-unset | promote | demote | … — optional duration/world/server on perm set");
            DashboardHttp.json(ex, 200, snap);
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").toLowerCase();
            switch (action) {
                case "save-ops" -> {
                    List<String> ops = DashboardAccessPermsCommands.parseList(body.get("ops"));
                    cfg.setOps(ops);
                    cfg.save();
                    for (String name : ops) {
                        server.executeCommand("op " + name);
                    }
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "ops", ops));
                }
                case "save-auto-op" -> {
                    boolean auto = !"false".equalsIgnoreCase(body.getOrDefault("autoOp", "false"));
                    cfg.setAutoOp(auto);
                    cfg.save();
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "autoOp", auto));
                }
                case "set-default-group" -> {
                    String group = body.getOrDefault("group", "default").trim().toLowerCase();
                    DashboardPermsSnapshotWriters.savePermsDefaultGroup(root, group);
                    server.executeCommand("yapperm reload");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "defaultGroup", group));
                }
                case "create-group", "save-group" -> handleSaveGroup(ex, root, body, action);
                case "apply-template", "clone-group" -> handleApplyTemplate(ex, root, body, action);
                case "save-group-nodes" -> handleSaveGroupNodes(ex, root, body);
                case "delete-group" -> handleDeleteGroup(ex, root, body);
                case "user-meta-set" -> handleUserMetaSet(ex, body);
                case "user-meta-clear" -> {
                    String player = body.getOrDefault("player", "").trim();
                    if (player.isEmpty()) {
                        DashboardHttp.json(ex, 400, Map.of("error", "player required"));
                        return;
                    }
                    String result = server.executeCommand("yapperm user " + player + " meta clear");
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result));
                }
                case "op" -> {
                    String p = body.getOrDefault("player", "").trim();
                    if (p.isEmpty()) {
                        DashboardHttp.json(ex, 400, Map.of("error", "player required"));
                        return;
                    }
                    List<String> ops = new ArrayList<>(cfg.getOps());
                    if (!ops.contains(p)) {
                        ops.add(p);
                        cfg.setOps(ops);
                        cfg.save();
                    }
                    String result = server.executeCommand("op " + p);
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result, "ops", ops));
                }
                case "deop" -> {
                    String p = body.getOrDefault("player", "").trim();
                    if (p.isEmpty()) {
                        DashboardHttp.json(ex, 400, Map.of("error", "player required"));
                        return;
                    }
                    List<String> ops = new ArrayList<>(cfg.getOps());
                    ops.removeIf(n -> n.equalsIgnoreCase(p));
                    cfg.setOps(ops);
                    cfg.save();
                    String result = server.executeCommand("deop " + p);
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result, "ops", ops));
                }
                default -> {
                    String cmd = DashboardAccessPermsCommands.permsCommand(action, body);
                    if (cmd == null) {
                        DashboardHttp.json(ex, 400, Map.of("error", "unknown action"));
                        return;
                    }
                    String result = server.executeCommand(cmd);
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "command", cmd, "result", result == null ? "" : result));
                }
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private void handleSaveGroup(HttpExchange ex, Path root, Map<String, String> body, String action) throws IOException {
        String name = body.getOrDefault("name", body.getOrDefault("group", "")).trim().toLowerCase();
        if (name.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "group name required"));
            return;
        }
        try {
            Integer weight = body.containsKey("weight") ? DashboardAccessPermsCommands.parseInt(body.get("weight"), 0) : null;
            String prefix = body.containsKey("prefix") ? body.get("prefix") : null;
            String suffix = body.containsKey("suffix") ? body.get("suffix") : null;
            String nameColor = body.containsKey("nameColor") ? body.get("nameColor")
                    : body.containsKey("name-color") ? body.get("name-color") : null;
            String chatColor = body.containsKey("chatColor") ? body.get("chatColor")
                    : body.containsKey("chat-color") ? body.get("chat-color") : null;
            List<String> parents = body.containsKey("parents") ? DashboardAccessPermsCommands.parseList(body.get("parents")) : null;
            if ("create-group".equals(action) && weight == null && prefix == null && suffix == null && parents == null) {
                weight = 0;
                prefix = "";
                suffix = "";
                parents = List.of();
            }
            DashboardPermsSnapshotWriters.savePermsGroup(root, name, weight, prefix, suffix,
                    nameColor, chatColor, parents);
            if ("true".equalsIgnoreCase(body.getOrDefault("addToTrack", "false"))) {
                String track = body.getOrDefault("track", "yap");
                DashboardPermsSnapshotWriters.appendGroupToTrack(root, track, name);
            }
            Map<String, Integer> nodes = applyPresetNodes(root, name, body);
            String apply = server.executeCommand("yapperm applypack");
            String editor = nodes != null ? server.executeCommand("yapperm editor-apply") : "";
            String reload = server.executeCommand("yapperm reload");
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("group", name);
            resp.put("applypack", apply == null ? "" : apply);
            resp.put("editor", editor == null ? "" : editor);
            resp.put("reload", reload == null ? "" : reload);
            if (nodes != null) {
                resp.put("allow", nodes.get("allow"));
                resp.put("deny", nodes.get("deny"));
            }
            DashboardHttp.json(ex, 200, resp);
        } catch (Exception e) {
            DashboardHttp.json(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleSaveGroupNodes(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        String group = body.getOrDefault("group", body.getOrDefault("name", "")).trim().toLowerCase();
        if (group.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "group required"));
            return;
        }
        try {
            Map<String, Integer> counts = DashboardPermsSnapshotWriters.savePermsGroupNodes(
                    root, group,
                    DashboardAccessPermsCommands.parseNodeList(body.get("allow")),
                    DashboardAccessPermsCommands.parseNodeList(body.get("deny")),
                    DashboardAccessPermsCommands.parseNodeList(body.get("unset")));
            String apply = server.executeCommand("yapperm editor-apply");
            String dump = server.executeCommand("yapperm dump");
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("group", group);
            resp.put("allow", counts.get("allow"));
            resp.put("deny", counts.get("deny"));
            resp.put("unset", counts.get("unset"));
            resp.put("apply", apply == null ? "" : apply);
            resp.put("dump", dump == null ? "" : dump);
            resp.put("groupNodes", DashboardNetworkSnapshots.perms(root).get("groupNodes"));
            DashboardHttp.json(ex, 200, resp);
        } catch (Exception e) {
            DashboardHttp.json(ex, 500, Map.of("error", e.getMessage() == null ? "save failed" : e.getMessage()));
        }
    }

    private void handleDeleteGroup(HttpExchange ex, Path root, Map<String, String> body) throws IOException {
        String name = body.getOrDefault("name", body.getOrDefault("group", "")).trim().toLowerCase();
        if (name.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "group name required"));
            return;
        }
        if ("default".equals(name)) {
            DashboardHttp.json(ex, 400, Map.of("error", "cannot delete the default group"));
            return;
        }
        try {
            DashboardPermsSnapshotWriters.deletePermsGroup(root, name);
            String deleted = server.executeCommand("yapperm group delete " + name);
            String reload = server.executeCommand("yapperm reload");
            DashboardHttp.json(ex, 200, Map.of(
                    "ok", true,
                    "group", name,
                    "delete", deleted == null ? "" : deleted,
                    "reload", reload == null ? "" : reload));
        } catch (Exception e) {
            DashboardHttp.json(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleUserMetaSet(HttpExchange ex, Map<String, String> body) throws IOException {
        String player = body.getOrDefault("player", "").trim();
        if (player.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "player required"));
            return;
        }
        String prefix = body.getOrDefault("prefix", "");
        String suffix = body.getOrDefault("suffix", "");
        String payload = prefix + META_SEP + suffix;
        String cmd = "yapperm user " + player + " meta set " + payload;
        String result = server.executeCommand(cmd);
        DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result));
    }

    private void handleApplyTemplate(HttpExchange ex, Path root, Map<String, String> body, String action)
            throws IOException {
        String group = body.getOrDefault("group", body.getOrDefault("name", "")).trim().toLowerCase();
        if (group.isEmpty()) {
            DashboardHttp.json(ex, 400, Map.of("error", "group required"));
            return;
        }
        try {
            if ("clone-group".equals(action) && body.getOrDefault("cloneFrom", "").isBlank()) {
                DashboardHttp.json(ex, 400, Map.of("error", "cloneFrom required"));
                return;
            }
            Map<String, Integer> nodes = applyPresetNodes(root, group, body);
            if (nodes == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "template, cloneFrom, or allow list required"));
                return;
            }
            String apply = server.executeCommand("yapperm editor-apply");
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("group", group);
            resp.put("allow", nodes.get("allow"));
            resp.put("deny", nodes.get("deny"));
            resp.put("apply", apply == null ? "" : apply);
            resp.put("groupNodes", DashboardNetworkSnapshots.perms(root).get("groupNodes"));
            DashboardHttp.json(ex, 200, resp);
        } catch (Exception e) {
            DashboardHttp.json(ex, 500, Map.of("error", e.getMessage() == null ? "apply failed" : e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> applyPresetNodes(Path root, String group, Map<String, String> body)
            throws IOException {
        List<String> allow = DashboardAccessPermsCommands.parseNodeList(body.get("allow"));
        List<String> deny = DashboardAccessPermsCommands.parseNodeList(body.get("deny"));
        String cloneFrom = body.getOrDefault("cloneFrom", "").trim().toLowerCase();
        String template = body.getOrDefault("template", "").trim().toLowerCase();
        if (!cloneFrom.isEmpty()) {
            Map<String, Object> perms = DashboardNetworkSnapshots.perms(root);
            Object raw = perms.get("groupNodes");
            if (raw instanceof Map<?, ?> all) {
                Object src = all.get(cloneFrom);
                if (src instanceof Map<?, ?> nodes) {
                    for (var e : ((Map<String, Object>) nodes).entrySet()) {
                        if (Boolean.TRUE.equals(e.getValue()) || "true".equalsIgnoreCase(String.valueOf(e.getValue()))) {
                            if (!allow.contains(e.getKey())) {
                                allow.add(e.getKey());
                            }
                        } else if (e.getValue() instanceof Boolean) {
                            if (!deny.contains(e.getKey())) {
                                deny.add(e.getKey());
                            }
                        }
                    }
                }
            }
        } else if (!template.isEmpty() && !"blank".equals(template)) {
            List<String> pack = PermissionCatalog.templates().getOrDefault(template, List.of());
            for (String node : pack) {
                if (!allow.contains(node)) {
                    allow.add(node);
                }
            }
        }
        if (allow.isEmpty() && deny.isEmpty()) {
            return null;
        }
        List<String> unset = new ArrayList<>();
        Map<String, Object> perms = DashboardNetworkSnapshots.perms(root);
        Object rawNodes = perms.get("groupNodes");
        if (rawNodes instanceof Map<?, ?> all && all.get(group) instanceof Map<?, ?> prev) {
            for (Object key : prev.keySet()) {
                String node = String.valueOf(key);
                if (!allow.contains(node) && !deny.contains(node)) {
                    unset.add(node);
                }
            }
        }
        return DashboardPermsSnapshotWriters.savePermsGroupNodes(root, group, allow, deny, unset);
    }

    private static List<Map<String, Object>> catalogWithDiscovered(Path root) {
        List<Map<String, Object>> cats = new ArrayList<>(PermissionCatalog.categories());
        java.util.Set<String> listed = new java.util.HashSet<>(PermissionCatalog.allNodes());
        Map<String, Object> extra = PluginPermissionScanner.discoveredCategory(
                root.resolve("plugins"), listed);
        Object nodes = extra.get("nodes");
        if (nodes instanceof List<?> list && !list.isEmpty()) {
            cats.add(extra);
        }
        return cats;
    }
}

package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.module.ModuleManager;
import com.yapcore.plugin.PluginManager;
import com.yapcore.resourcepack.ResourcePackManager;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.PluginCompatMatrix;
import com.yapcore.web.PluginCompatMatrix.Lookup;
import com.yapcore.web.TinyJson;
import com.yapcore.web.auth.DashboardAuth;
import com.yapcore.web.http.DashboardHttp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardPluginsApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardPluginsApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    public void apiPlugins(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        PluginManager pm = server.getPluginManager();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            List<Map<String, Object>> list = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (var p : pm.listPlugins()) {
                names.add(p.fileName());
            }
            List<Map<String, Object>> warnings = PluginCompatMatrix.warningsForInstalled(names);
            for (var p : pm.listPlugins()) {
                Lookup compat = PluginCompatMatrix.lookup(p.fileName());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fileName", p.fileName());
                row.put("sizeLabel", p.sizeLabel());
                row.put("sizeBytes", p.sizeBytes());
                row.put("compatStatus", compat.status());
                row.put("nativeAlternative", compat.nativeAlternative());
                row.put("compatNote", compat.note());
                row.put("compatWarning", compat.hasWarning());
                list.add(row);
            }
            DashboardHttp.json(ex, 200, Map.of(
                    "plugins", list,
                    "compatWarnings", warnings,
                    "matrixSize", PluginCompatMatrix.all().size()));
            return;
        }
        if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String name = body.getOrDefault("fileName", "");
            boolean ok = pm.removePlugin(name);
            DashboardHttp.json(ex, 200, Map.of("ok", ok));
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String path = body.getOrDefault("path", "");
            if (path.isBlank()) {
                DashboardHttp.json(ex, 400, Map.of("error", "provide path to a .jar on the server"));
                return;
            }
            try {
                var info = pm.addPlugin(Path.of(path));
                DashboardHttp.json(ex, 200, Map.of("ok", true, "fileName", info.fileName()));
            } catch (Exception e) {
                DashboardHttp.json(ex, 400, Map.of("ok", false, "error", e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiModules(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        ModuleManager mm = server.getModuleManager();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (var m : mm.listModules()) {
                list.add(Map.of(
                        "fileName", m.fileName(),
                        "sizeLabel", m.sizeLabel(),
                        "sizeBytes", m.sizeBytes()));
            }
            DashboardHttp.json(ex, 200, Map.of("modules", list));
            return;
        }
        if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            boolean ok = mm.removeModule(body.getOrDefault("fileName", ""));
            DashboardHttp.json(ex, 200, Map.of("ok", ok));
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            try {
                var info = mm.addModule(Path.of(body.getOrDefault("path", "")));
                DashboardHttp.json(ex, 200, Map.of("ok", true, "fileName", info.fileName()));
            } catch (Exception e) {
                DashboardHttp.json(ex, 400, Map.of("ok", false, "error", e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiPacks(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        ResourcePackManager packs = server.getResourcePacks();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            List<Map<String, Object>> list = new ArrayList<>();
            var actives = packs.getActivePacks().stream().map(p -> p.getFileName()).toList();
            for (var p : packs.listPacks()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fileName", p.getFileName());
                row.put("active", actives.contains(p.getFileName()));
                row.put("sizeLabel", p.sizeLabel());
                list.add(row);
            }
            DashboardHttp.json(ex, 200, Map.of("packs", list, "active", actives, "activeCount", actives.size()));
            return;
        }
        Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            String action = body.getOrDefault("action", "setActive");
            try {
                if ("setActive".equals(action)) {
                    String raw = body.getOrDefault("fileName", body.getOrDefault("fileNames", ""));
                    List<String> names = new ArrayList<>();
                    for (String part : raw.split(",")) {
                        if (!part.isBlank()) {
                            names.add(part.trim());
                        }
                    }
                    packs.setActivePacks(names);
                } else if ("addActive".equals(action)) {
                    packs.addActivePack(body.getOrDefault("fileName", ""));
                } else if ("removeActive".equals(action)) {
                    packs.removeActivePack(body.getOrDefault("fileName", ""));
                } else if ("clear".equals(action)) {
                    packs.setActivePacks(List.of());
                } else if ("remove".equals(action)) {
                    packs.removePack(body.getOrDefault("fileName", ""));
                } else if ("add".equals(action)) {
                    packs.addPack(Path.of(body.getOrDefault("path", "")));
                }
                DashboardHttp.json(ex, 200, Map.of("ok", true,
                        "active", packs.getActivePacks().stream().map(p -> p.getFileName()).toList()));
            } catch (Exception e) {
                DashboardHttp.json(ex, 400, Map.of("ok", false, "error", e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }
}

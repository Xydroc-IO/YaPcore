package com.yapcore.web.api;

import com.sun.net.httpserver.HttpExchange;
import com.yapcore.module.ModuleManager;
import com.yapcore.plugin.PluginManager;
import com.yapcore.plugin.YapPluginControl;
import com.yapcore.resourcepack.ResourcePackManager;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.web.DashboardNetworkSnapshots;
import com.yapcore.web.PluginCompatMatrix;
import com.yapcore.web.PluginCompatMatrix.Lookup;
import com.yapcore.web.PluginConfigCatalog;
import com.yapcore.web.PluginConfigHints;
import com.yapcore.web.PluginConfigIo;
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

public final class DashboardPluginsApi {

    private final YaPcoreServer server;
    private final DashboardAuth auth;

    public DashboardPluginsApi(YaPcoreServer server, DashboardAuth auth) {
        this.server = server;
        this.auth = auth;
    }

    private YapPluginControl control() {
        return new YapPluginControl(server.getRootDir(), server.getPluginManager());
    }

    public void apiPlugins(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        YapPluginControl ctrl = control();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            List<Map<String, Object>> list = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (Map<String, Object> row : ctrl.listDetailed()) {
                String active = String.valueOf(row.getOrDefault("activeName", row.get("fileName")));
                names.add(active);
                Lookup compat = PluginCompatMatrix.lookup(active);
                row.put("compatStatus", compat.status());
                row.put("nativeAlternative", compat.nativeAlternative());
                row.put("compatNote", compat.note());
                row.put("compatWarning", compat.hasWarning());
                list.add(row);
            }
            List<Map<String, Object>> warnings = PluginCompatMatrix.warningsForInstalled(names);
            DashboardHttp.json(ex, 200, Map.of(
                    "plugins", list,
                    "compatWarnings", warnings,
                    "matrixSize", PluginCompatMatrix.all().size()));
            return;
        }
        if ("DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String name = body.getOrDefault("fileName", "");
            boolean force = DashboardHttp.bool(body.getOrDefault("force", "false"));
            try {
                Map<String, Object> result = ctrl.uninstall(name, force);
                DashboardHttp.json(ex, 200, result);
            } catch (Exception e) {
                DashboardHttp.json(ex, 400, Map.of("ok", false, "error", e.getMessage() == null ? "uninstall failed" : e.getMessage()));
            }
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "").trim().toLowerCase(Locale.ROOT);
            try {
                if (action.isBlank() || "install".equals(action)) {
                    String path = body.getOrDefault("path", "");
                    if (path.isBlank()) {
                        DashboardHttp.json(ex, 400, Map.of("error", "provide path to a .jar under YAPCORE_HOME"));
                        return;
                    }
                    var info = ctrl.install(Path.of(path));
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "fileName", info.fileName(), "needsRestart", true));
                    return;
                }
                if ("enable".equals(action) || "disable".equals(action)) {
                    String fileName = body.getOrDefault("fileName", "");
                    YapPluginControl.Mode mode = "hard".equalsIgnoreCase(body.getOrDefault("mode", "soft"))
                            ? YapPluginControl.Mode.HARD
                            : YapPluginControl.Mode.SOFT;
                    boolean force = DashboardHttp.bool(body.getOrDefault("force", "false"));
                    Map<String, Object> result = ctrl.setEnabled(fileName, "enable".equals(action), mode, force);
                    if (mode == YapPluginControl.Mode.SOFT) {
                        String reload = String.valueOf(result.getOrDefault("reload", ""));
                        if (!reload.isBlank()) {
                            String reloadOut = server.executeCommand(reload);
                            result.put("reloadResult", reloadOut == null ? "" : reloadOut);
                        }
                    }
                    DashboardHttp.json(ex, 200, result);
                    return;
                }
                DashboardHttp.json(ex, 400, Map.of("error", "unknown action (install|enable|disable)"));
            } catch (Exception e) {
                DashboardHttp.json(ex, 400, Map.of("ok", false, "error", e.getMessage() == null ? "failed" : e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    public void apiPluginConfig(HttpExchange ex) throws IOException {
        if (!auth.requireAuth(ex)) {
            return;
        }
        Path root = server.getRootDir();
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            String query = ex.getRequestURI().getQuery();
            String pluginId = queryValue(query, "plugin");
            if (pluginId != null && !pluginId.isBlank()) {
                PluginConfigCatalog.Entry entry = PluginConfigCatalog.byId(pluginId);
                if (entry == null) {
                    DashboardHttp.json(ex, 404, Map.of("error", "unknown plugin"));
                    return;
                }
                DashboardHttp.json(ex, 200, pluginDetail(root, entry));
                return;
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (PluginConfigCatalog.Entry entry : PluginConfigCatalog.all()) {
                list.add(pluginSummary(root, entry));
            }
            DashboardHttp.json(ex, 200, Map.of("ok", true, "plugins", list));
            return;
        }
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String, String> body = TinyJson.parseFlatObject(DashboardHttp.readBody(ex));
            String action = body.getOrDefault("action", "save").toLowerCase();
            PluginConfigCatalog.Entry entry = PluginConfigCatalog.byId(body.get("plugin"));
            if (entry == null) {
                DashboardHttp.json(ex, 400, Map.of("error", "plugin required"));
                return;
            }
            try {
                if ("reload".equals(action)) {
                    String result = entry.reload().isBlank() ? "no reload command"
                            : server.executeCommand(entry.reload());
                    DashboardHttp.json(ex, 200, Map.of("ok", true, "result", result == null ? "" : result));
                    return;
                }
                PluginConfigIo.save(root, entry, body);
                String result = "";
                if (!entry.reload().isBlank()) {
                    result = server.executeCommand(entry.reload());
                }
                DashboardHttp.json(ex, 200, Map.of(
                        "ok", true,
                        "plugin", entry.id(),
                        "reload", result == null ? "" : result,
                        "fields", PluginConfigIo.flatten(PluginConfigIo.load(root, entry)).size()));
            } catch (Exception e) {
                DashboardHttp.json(ex, 500, Map.of("error", e.getMessage() == null ? "save failed" : e.getMessage()));
            }
            return;
        }
        ex.sendResponseHeaders(405, -1);
    }

    private static Map<String, Object> pluginSummary(Path root, PluginConfigCatalog.Entry entry) {
        Path file = PluginConfigIo.configPath(root, entry);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entry.id());
        row.put("title", PluginConfigHints.pluginTitle(entry.id(), entry.title()));
        row.put("blurb", PluginConfigHints.pluginBlurb(entry.id()));
        row.put("dataDir", entry.dataDir());
        row.put("file", entry.file());
        row.put("reload", entry.reload());
        row.put("installed", jarPresent(root.resolve("plugins"), entry.jarToken()));
        row.put("configPresent", java.nio.file.Files.isRegularFile(file));
        int fields = 0;
        if (java.nio.file.Files.isRegularFile(file)) {
            try {
                fields = PluginConfigIo.flatten(DashboardNetworkSnapshots.loadYaml(file)).size();
            } catch (Exception ignored) {
                fields = 0;
            }
        }
        row.put("fields", fields);
        return row;
    }

    private static Map<String, Object> pluginDetail(Path root, PluginConfigCatalog.Entry entry) throws IOException {
        Map<String, Object> out = pluginSummary(root, entry);
        out.put("ok", true);
        out.put("fields", PluginConfigIo.flatten(PluginConfigIo.load(root, entry)));
        return out;
    }

    private static boolean jarPresent(Path pluginsDir, String token) {
        if (!java.nio.file.Files.isDirectory(pluginsDir) || token == null || token.isBlank()) {
            return false;
        }
        try (var stream = java.nio.file.Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (var jar : stream) {
                if (jar.getFileName().toString().toLowerCase().contains(token.toLowerCase())) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static String queryValue(String query, String key) {
        if (query == null || query.isBlank()) {
            return "";
        }
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && key.equals(part.substring(0, eq))) {
                return java.net.URLDecoder.decode(part.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return "";
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

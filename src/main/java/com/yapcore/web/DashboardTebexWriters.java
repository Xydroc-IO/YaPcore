package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** YAML writers for third-party Tebex plugin config. */
public final class DashboardTebexWriters {

    private DashboardTebexWriters() {
    }

    public static void saveSecret(Path root, String secret) throws IOException {
        Path file = root.resolve("plugins").resolve("Tebex").resolve("config.yml");
        Map<String, Object> yaml = Files.isRegularFile(file)
                ? DashboardNetworkSnapshots.loadYaml(file)
                : new LinkedHashMap<>();
        Map<String, Object> server = DashboardNetworkSnapshots.mapOrCreate(yaml, "server");
        server.put("secret-key", secret == null ? "" : secret.trim());
        if (!yaml.containsKey("buy-command")) {
            Map<String, Object> buy = new LinkedHashMap<>();
            buy.put("enabled", true);
            buy.put("name", "buy");
            yaml.put("buy-command", buy);
        }
        if (!server.containsKey("proxy")) {
            server.put("proxy", false);
        }
        if (!yaml.containsKey("check-for-updates")) {
            yaml.put("check-for-updates", true);
        }
        if (!yaml.containsKey("auto-report-enabled")) {
            yaml.put("auto-report-enabled", true);
        }
        ensureGuiDefaults(yaml);
        if (!yaml.containsKey("config-version")) {
            yaml.put("config-version", 2);
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    public static void saveSettings(Path root, Map<String, String> body) throws IOException {
        Path file = root.resolve("plugins").resolve("Tebex").resolve("config.yml");
        Map<String, Object> yaml = Files.isRegularFile(file)
                ? DashboardNetworkSnapshots.loadYaml(file)
                : new LinkedHashMap<>();
        Map<String, Object> buy = DashboardNetworkSnapshots.mapOrCreate(yaml, "buy-command");
        if (body.containsKey("buyCommandEnabled")) {
            buy.put("enabled", !"false".equalsIgnoreCase(body.get("buyCommandEnabled")));
        }
        if (body.containsKey("buyCommandName")) {
            String name = body.get("buyCommandName");
            if (name != null && !name.isBlank()) {
                buy.put("name", name.trim().replaceAll("\\s+", ""));
            }
        }
        Map<String, Object> server = DashboardNetworkSnapshots.mapOrCreate(yaml, "server");
        if (body.containsKey("proxyMode")) {
            server.put("proxy", !"false".equalsIgnoreCase(body.get("proxyMode")));
        }
        if (body.containsKey("verbose")) {
            yaml.put("verbose", !"false".equalsIgnoreCase(body.get("verbose")));
        }
        if (body.containsKey("checkForUpdates")) {
            yaml.put("check-for-updates", !"false".equalsIgnoreCase(body.get("checkForUpdates")));
        }
        if (body.containsKey("autoReportEnabled")) {
            yaml.put("auto-report-enabled", !"false".equalsIgnoreCase(body.get("autoReportEnabled")));
        }
        Map<String, Object> gui = DashboardNetworkSnapshots.mapOrCreate(yaml, "gui");
        Map<String, Object> menu = DashboardNetworkSnapshots.mapOrCreate(gui, "menu");
        Map<String, Object> home = DashboardNetworkSnapshots.mapOrCreate(menu, "home");
        if (body.containsKey("guiHomeTitle")) {
            String title = body.get("guiHomeTitle");
            if (title != null && !title.isBlank()) {
                home.put("title", title.trim());
            }
        }
        if (body.containsKey("guiHomeRows")) {
            try {
                int rows = Integer.parseInt(body.get("guiHomeRows").trim());
                home.put("rows", Math.max(1, Math.min(6, rows)));
            } catch (NumberFormatException ignored) {
            }
        }
        if (!yaml.containsKey("config-version")) {
            yaml.put("config-version", 2);
        }
        DashboardNetworkSnapshots.dumpYaml(file, yaml);
    }

    private static void ensureGuiDefaults(Map<String, Object> yaml) {
        Map<String, Object> gui = DashboardNetworkSnapshots.mapOrCreate(yaml, "gui");
        Map<String, Object> menu = DashboardNetworkSnapshots.mapOrCreate(gui, "menu");
        Map<String, Object> home = DashboardNetworkSnapshots.mapOrCreate(menu, "home");
        if (!home.containsKey("title")) {
            home.put("title", "Server Shop");
        }
        if (!home.containsKey("rows")) {
            home.put("rows", 3);
        }
    }
}

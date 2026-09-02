package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Map, NPC, and Phase 8 ops-plugin snapshots for the dashboard. */
public final class DashboardOpsSnapshots {

    private DashboardOpsSnapshots() {
    }

    public static Map<String, Object> map(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "yap-map", "YaPMap");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPMap", "config.yml");
        Map<String, Object> http = DashboardNetworkSnapshots.map(yaml.get("http"));
        boolean usePackServer = DashboardNetworkSnapshots.bool(http.get("use-yapcore-server"), true);
        Map<String, String> serverProps = loadServerProperties(root);
        int packPort = DashboardNetworkSnapshots.intVal(serverProps.get("resource-pack-http-port"), 8081);
        int dashPort = DashboardNetworkSnapshots.intVal(serverProps.get("web-dashboard-port"), 8080);
        String bind = DashboardNetworkSnapshots.str(http.get("bind"), "127.0.0.1");
        int port = DashboardNetworkSnapshots.intVal(http.get("port"), 8082);
        if (usePackServer) {
            port = packPort;
            bind = "127.0.0.1";
        }
        out.put("bindHost", bind);
        out.put("httpPort", port);
        out.put("dashboardPort", dashPort);
        out.put("usePackServer", usePackServer);
        // Same-origin path — works locally and when dashboard is accessed remotely
        out.put("mapUrl", "/map/");
        out.put("packMapUrl", "http://127.0.0.1:" + packPort + "/map/");
        out.put("worlds", DashboardNetworkSnapshots.stringList(yaml.get("worlds"), List.of("world")));
        out.put("renderIntervalMinutes", DashboardNetworkSnapshots.intVal(yaml.get("render-interval-minutes"), 15));
        out.put("maxHeight", DashboardNetworkSnapshots.intVal(yaml.get("max-height"), 320));
        out.put("sampleChunkRadius", DashboardNetworkSnapshots.intVal(yaml.get("sample-chunk-radius"), 8));
        Path tiles = root.resolve("plugins").resolve("YaPMap").resolve("map/tiles");
        int tileCount = countFilesRecursive(tiles, ".png");
        out.put("tileCount", tileCount);
        out.put("tilesDir", tiles.toString());
        out.put("webReady", Files.isRegularFile(root.resolve("plugins").resolve("YaPMap").resolve("web/index.html")));
        out.put("mapReady", tileCount > 0 && DashboardNetworkSnapshots.bool(out.get("webReady"), false));
        return out;
    }

    static Map<String, String> loadServerProperties(Path root) {
        Map<String, String> out = new LinkedHashMap<>();
        Path file = root.resolve("config").resolve("server.properties");
        if (!Files.isRegularFile(file)) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                int eq = t.indexOf('=');
                if (eq > 0) {
                    out.put(t.substring(0, eq).trim(), t.substring(eq + 1).trim());
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    /** Compact Phase 8 ops-plugin readiness for the dashboard status tab. */
    public static Map<String, Object> opsPhase8Summary(Path root) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(opsRow("Protect", DashboardNetworkSnapshots.protect(root), snap -> {
            if (!DashboardNetworkSnapshots.bool(snap.get("installed"), false)) {
                return "missing";
            }
            return DashboardNetworkSnapshots.bool(snap.get("loggingEnabled"), true) ? "logging on" : "logging off";
        }));
        rows.add(opsRow("Chat", DashboardNetworkSnapshots.chat(root), snap -> {
            if (!DashboardNetworkSnapshots.bool(snap.get("installed"), false)) {
                return "missing";
            }
            int channels = snap.get("channels") instanceof List<?> list ? list.size() : 0;
            return channels + " ch · slow " + DashboardNetworkSnapshots.intVal(snap.get("slowModeSeconds"), 0) + "s";
        }));
        rows.add(opsRow("Moderation", DashboardNetworkSnapshots.moderation(root), snap ->
                DashboardNetworkSnapshots.bool(snap.get("installed"), false) ? "ready" : "missing"));
        rows.add(opsRow("Player data", DashboardNetworkSnapshots.playerdata(root), snap -> {
            if (!DashboardNetworkSnapshots.bool(snap.get("installed"), false)) {
                return "missing";
            }
            return DashboardNetworkSnapshots.bool(snap.get("economyEnabled"), true) ? "economy on" : "economy off";
        }));
        rows.add(opsRow("Map", map(root), snap -> {
            if (!DashboardNetworkSnapshots.bool(snap.get("installed"), false)) {
                return "missing";
            }
            return DashboardNetworkSnapshots.bool(snap.get("mapReady"), false)
                    ? DashboardNetworkSnapshots.intVal(snap.get("tileCount"), 0) + " tiles"
                    : "awaiting render";
        }));
        rows.add(opsRow("Discord", DashboardNetworkSnapshots.discord(root), snap -> {
            if (!DashboardNetworkSnapshots.bool(snap.get("installed"), false)) {
                return "missing";
            }
            boolean hooks = DashboardNetworkSnapshots.bool(snap.get("moderationConfigured"), false)
                    || DashboardNetworkSnapshots.bool(snap.get("chatConfigured"), false);
            if (hooks) {
                return "webhooks set";
            }
            return "webhooks empty";
        }));
        long installed = rows.stream().filter(r -> Boolean.TRUE.equals(r.get("installed"))).count();
        out.put("plugins", rows);
        out.put("installedCount", installed);
        out.put("totalCount", rows.size());
        out.put("summary", installed == rows.size()
                ? "All Phase 8 ops plugins present"
                : installed + " / " + rows.size() + " ops plugins installed");
        return out;
    }

    static Map<String, Object> opsRow(String label, Map<String, Object> snap,
                                              java.util.function.Function<Map<String, Object>, String> detailFn) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("installed", DashboardNetworkSnapshots.bool(snap.get("installed"), false));
        row.put("configPresent", DashboardNetworkSnapshots.bool(snap.get("configPresent"), false));
        row.put("detail", detailFn.apply(snap));
        return row;
    }

    public static Map<String, Object> npcs(Path root) {
        Map<String, Object> out = DashboardNetworkSnapshots.base(root, "yap-npcs", "YaPNpcs");
        Map<String, Object> yaml = DashboardNetworkSnapshots.yaml(root, "YaPNpcs", "config.yml");
        out.put("serverId", DashboardNetworkSnapshots.str(yaml.get("server-id"), "default"));
        Map<String, Object> dialogue = DashboardNetworkSnapshots.map(yaml.get("dialogue"));
        out.put("defaultDialogue", DashboardNetworkSnapshots.str(dialogue.get("default"), "&7Hello, traveler!"));
        Path questDir = root.resolve("plugins").resolve("YaPNpcs").resolve("quests");
        out.put("questPackCount", DashboardNetworkSnapshots.countFiles(questDir, ".yml", ".yaml"));
        out.put("questIds", listQuestIds(questDir));
        return out;
    }

    static List<String> listQuestIds(Path questDir) {
        List<String> ids = new ArrayList<>();
        if (!Files.isDirectory(questDir)) {
            return ids;
        }
        try (var stream = Files.list(questDir)) {
            stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".yml") || n.endsWith(".yaml");
            }).forEach(path -> {
                try {
                    Map<String, Object> doc = DashboardNetworkSnapshots.loadYaml(path);
                    Object quests = doc.get("quests");
                    if (quests instanceof Map<?, ?> map) {
                        map.keySet().forEach(k -> ids.add(String.valueOf(k)));
                    }
                } catch (Exception ignored) {
                    /* skip bad pack */
                }
            });
        } catch (IOException ignored) {
            return ids;
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    static int countFilesRecursive(Path dir, String suffix) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int count = 0;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.toList()) {
                if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix)) {
                    count++;
                }
            }
        } catch (IOException ignored) {
        }
        return count;
    }
}

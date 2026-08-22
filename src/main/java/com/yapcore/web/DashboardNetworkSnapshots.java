package com.yapcore.web;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Config snapshots for network plugin dashboard tabs (Phase 8). */
public final class DashboardNetworkSnapshots {

    private DashboardNetworkSnapshots() {
    }

    public static Map<String, Object> protect(Path root) {
        Map<String, Object> out = base(root, "yap-protect", "YaPProtect");
        Map<String, Object> yaml = yaml(root, "YaPProtect", "config.yml");
        Map<String, Object> logging = map(yaml.get("logging"));
        out.put("loggingEnabled", bool(logging.get("enabled"), true));
        out.put("logBlocks", bool(logging.get("block-break"), true));
        out.put("logContainers", bool(logging.get("container-inventory"), true));
        out.put("pruneDays", intVal(nested(yaml, "retention", "prune-days"), 30));
        out.put("serverId", str(yaml.get("server-id"), "default"));
        return out;
    }

    public static Map<String, Object> world(Path root) {
        Map<String, Object> out = base(root, "yap-world", "YaPWorld");
        Map<String, Object> yaml = yaml(root, "YaPWorld", "config.yml");
        Map<String, Object> worlds = map(yaml.get("worlds"));
        Map<String, Object> schem = map(yaml.get("schematics"));
        out.put("allowLoad", bool(worlds.get("allow-load"), true));
        out.put("allowUnload", bool(worlds.get("allow-unload"), true));
        out.put("schematicsEnabled", bool(schem.get("enabled"), true));
        out.put("schematicsFolder", str(schem.get("folder"), "schematics"));
        out.put("brushMaxRadius", intVal(nested(yaml, "brush", "max-radius"), 16));
        out.put("serverId", str(yaml.get("server-id"), "default"));
        Path schemDir = root.resolve("plugins").resolve("YaPWorld").resolve(str(schem.get("folder"), "schematics"));
        out.put("schematicCount", countFiles(schemDir, ".yschem", ".schem"));
        return out;
    }

    public static Map<String, Object> chat(Path root) {
        Map<String, Object> out = base(root, "yap-chat", "YaPChat");
        Map<String, Object> yaml = yaml(root, "YaPChat", "config.yml");
        out.put("defaultChannel", str(yaml.get("default-channel"), "global"));
        out.put("slowModeSeconds", intVal(yaml.get("slow-mode-seconds"), 0));
        out.put("unsignedSystemChat", bool(yaml.get("unsigned-system-chat"), true));
        out.put("networkEnabled", bool(nestedBool(yaml, "network", "enabled"), true));
        Map<String, Object> filter = map(yaml.get("filter"));
        out.put("filterEnabled", bool(filter.get("enabled"), true));
        out.put("channels", channelNames(yaml.get("channels")));
        out.put("serverId", str(yaml.get("server-id"), "default"));
        return out;
    }

    public static Map<String, Object> moderation(Path root) {
        Map<String, Object> out = base(root, "yap-moderation", "YaPModeration");
        Map<String, Object> yaml = yaml(root, "YaPModeration", "config.yml");
        out.put("serverId", str(yaml.get("server-id"), "default"));
        out.put("useSharedYapdb", bool(yaml.get("use-shared-yapdb"), true));
        out.put("kickMessage", str(yaml.get("kick-message"), ""));
        out.put("banMessage", str(yaml.get("ban-message"), ""));
        return out;
    }

    public static Map<String, Object> perms(Path root) {
        Map<String, Object> out = base(root, "yap-perms", "YaPPerms");
        Map<String, Object> yaml = yaml(root, "YaPPerms", "config.yml");
        out.put("defaultGroup", str(yaml.get("default-group"), "default"));
        out.put("defaultTrack", str(yaml.get("default-track"), "yap"));
        out.put("useSharedYapdb", bool(yaml.get("use-shared-yapdb"), true));
        out.put("groups", groupNames(yaml.get("groups")));
        return out;
    }

    public static Map<String, Object> playerdata(Path root) {
        Map<String, Object> out = base(root, "yap-playerdata", "YaPPlayerData");
        Map<String, Object> yaml = yaml(root, "YaPPlayerData", "config.yml");
        out.put("serverId", str(yaml.get("server-id"), "default"));
        out.put("economyEnabled", bool(nestedBool(yaml, "economy", "enabled"), true));
        out.put("features", featureBools(yaml.get("features"), List.of(
                "homes", "warps", "kits", "mail", "shops", "jobs", "auctions", "claims", "traders")));
        out.put("authEnabled", bool(nestedBool(yaml, "auth", "enabled"), false));
        out.put("syncInventory", bool(nestedBool(yaml, "sync", "inventory"), true));
        out.put("claimsEnabled", bool(nestedBool(yaml, "claims", "enabled"), true));
        return out;
    }

    public static Map<String, Object> discord(Path root) {
        Map<String, Object> out = base(root, "yap-discord", "YaPDiscord");
        Map<String, Object> yaml = yaml(root, "YaPDiscord", "config.yml");
        Map<String, Object> hooks = map(yaml.get("webhooks"));
        out.put("moderationConfigured", !str(hooks.get("moderation"), "").isBlank());
        out.put("chatConfigured", !str(hooks.get("chat"), "").isBlank());
        out.put("mcToDiscord", bool(nestedBool(yaml, "relay", "mc-to-discord"), false));
        out.put("discordToMc", bool(nestedBool(yaml, "relay", "discord-to-mc"), false));
        out.put("inboundEnabled", bool(nestedBool(yaml, "inbound", "enabled"), true));
        out.put("inboundPort", intVal(nested(yaml, "inbound", "port"), 8765));
        out.put("inboundSecretConfigured", !str(nested(yaml, "inbound", "secret"), "").isBlank()
                && !"change-me".equals(str(nested(yaml, "inbound", "secret"), "")));
        return out;
    }

    public static Map<String, Object> tab(Path root) {
        Map<String, Object> out = base(root, "yap-tab", "YaPTab");
        Map<String, Object> yaml = yaml(root, "YaPTab", "config.yml");
        out.put("header", lines(yaml.get("header")));
        out.put("footer", lines(yaml.get("footer")));
        Map<String, Object> sidebar = map(yaml.get("sidebar"));
        out.put("sidebarLines", lines(sidebar.get("lines")));
        out.put("sidebarEnabled", bool(sidebar.get("enabled"), true));
        out.put("nametagTeams", bool(yaml.get("nametag-teams"), true));
        out.put("refreshSeconds", intVal(yaml.get("refresh-seconds"), 3));
        out.put("networkSyncEnabled", bool(nestedBool(yaml, "network-sync", "enabled"), true));
        Map<String, Object> bossbar = map(yaml.get("bossbar"));
        out.put("bossBarEnabled", bool(bossbar.get("enabled"), false));
        out.put("bossBarWelcomeOnJoin", bool(bossbar.get("welcome-on-join"), true));
        out.put("bossBarTitle", str(bossbar.get("title"), "&6&lWelcome to YaP"));
        out.put("bossBarSubtitle", str(bossbar.get("subtitle"), "&7Enjoy your stay"));
        out.put("bossBarColor", str(bossbar.get("color"), "YELLOW"));
        out.put("bossBarDurationSeconds", intVal(bossbar.get("duration-seconds"), 8));
        return out;
    }

    public static Map<String, Object> map(Path root) {
        Map<String, Object> out = base(root, "yap-map", "YaPMap");
        Map<String, Object> yaml = yaml(root, "YaPMap", "config.yml");
        Map<String, Object> http = map(yaml.get("http"));
        String bind = str(http.get("bind"), "127.0.0.1");
        int port = intVal(http.get("port"), 8081);
        out.put("bindHost", bind);
        out.put("httpPort", port);
        out.put("mapUrl", "http://" + bind + ":" + port + "/map/");
        out.put("worlds", stringList(yaml.get("worlds"), List.of("world")));
        out.put("renderIntervalMinutes", intVal(yaml.get("render-interval-minutes"), 15));
        out.put("maxHeight", intVal(yaml.get("max-height"), 320));
        out.put("sampleChunkRadius", intVal(yaml.get("sample-chunk-radius"), 8));
        Path tiles = root.resolve("plugins").resolve("YaPMap").resolve("map/tiles");
        out.put("tileCount", countFilesRecursive(tiles, ".png"));
        out.put("tilesDir", tiles.toString());
        return out;
    }

    public static Map<String, Object> guard(Path root) {
        Map<String, Object> out = base(root, "yap-guard", "YaPGuard");
        Map<String, Object> yaml = yaml(root, "YaPGuard", "config.yml");
        Map<String, Object> checks = map(yaml.get("checks"));
        out.put("flyEnabled", checkEnabled(checks, "fly"));
        out.put("speedEnabled", checkEnabled(checks, "speed"));
        out.put("reachEnabled", checkEnabled(checks, "reach"));
        out.put("scaffoldEnabled", checkEnabled(checks, "scaffold"));
        out.put("maxViolationsBeforeKick", intVal(yaml.get("max-violations-before-kick"), 8));
        out.put("alertsEnabled", bool(yaml.get("alerts-enabled"), true));
        out.put("violationDecaySeconds", intVal(yaml.get("violation-decay-seconds"), 45));
        return out;
    }

    public static Map<String, Object> regions(Path root) {
        Map<String, Object> out = base(root, "yap-regions", "YaPRegions");
        Map<String, Object> yaml = yaml(root, "YaPRegions", "config.yml");
        out.put("serverId", str(yaml.get("server-id"), "default"));
        out.put("flags", List.of(
                "pvp", "mob-damage", "build", "interact", "entry", "chest-access", "fire-spread", "mob-spawning"));
        return out;
    }

    public static Map<String, Object> npcs(Path root) {
        Map<String, Object> out = base(root, "yap-npcs", "YaPNpcs");
        Map<String, Object> yaml = yaml(root, "YaPNpcs", "config.yml");
        out.put("serverId", str(yaml.get("server-id"), "default"));
        Map<String, Object> dialogue = map(yaml.get("dialogue"));
        out.put("defaultDialogue", str(dialogue.get("default"), "&7Hello, traveler!"));
        Path questDir = root.resolve("plugins").resolve("YaPNpcs").resolve("quests");
        out.put("questPackCount", countFiles(questDir, ".yml", ".yaml"));
        return out;
    }

    private static boolean checkEnabled(Map<String, Object> checks, String key) {
        return bool(map(checks.get(key)).get("enabled"), true);
    }

    private static List<String> stringList(Object val, List<String> fallback) {
        if (val instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return fallback;
    }

    private static int countFilesRecursive(Path dir, String suffix) {
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

    private static Map<String, Object> base(Path root, String jarToken, String dataDir) {
        Map<String, Object> out = new LinkedHashMap<>();
        Path pluginsDir = root.resolve("plugins");
        out.put("installed", jarPresent(pluginsDir, jarToken));
        out.put("configPresent", Files.isRegularFile(pluginsDir.resolve(dataDir).resolve("config.yml")));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(Path root, String dataDir, String fileName) {
        Path file = root.resolve("plugins").resolve(dataDir).resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            return loadYaml(file);
        } catch (IOException e) {
            return Map.of();
        }
    }

    private static List<String> channelNames(Object channels) {
        if (!(channels instanceof Map<?, ?> map)) {
            return List.of("global", "local", "staff");
        }
        List<String> names = new ArrayList<>();
        for (Object key : map.keySet()) {
            names.add(String.valueOf(key));
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private static List<String> groupNames(Object groups) {
        if (!(groups instanceof Map<?, ?> map)) {
            return List.of("default", "vip", "mod", "admin");
        }
        List<String> names = new ArrayList<>();
        for (Object key : map.keySet()) {
            names.add(String.valueOf(key));
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private static Map<String, Boolean> featureBools(Object features, List<String> keys) {
        Map<String, Object> map = map(features);
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (String key : keys) {
            out.put(key, bool(map.get(key), false));
        }
        return out;
    }

    private static List<String> lines(Object val) {
        List<String> out = new ArrayList<>();
        if (val instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
        } else if (val instanceof String s && !s.isBlank()) {
            out.add(s);
        }
        return out;
    }

    private static int countFiles(Path dir, String... suffixes) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                for (String suf : suffixes) {
                    if (name.endsWith(suf)) {
                        count++;
                        break;
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> loadYaml(Path file) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(file)) {
            Object loaded = yaml.load(in);
            if (loaded instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
        }
        return new LinkedHashMap<>();
    }

    static void dumpYaml(Path file, Map<String, Object> data) throws IOException {
        Files.createDirectories(file.getParent());
        org.yaml.snakeyaml.DumperOptions opts = new org.yaml.snakeyaml.DumperOptions();
        opts.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        opts.setIndent(2);
        Yaml yaml = new Yaml(opts);
        Files.writeString(file, yaml.dump(data), java.nio.charset.StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> mapOrCreate(Map<String, Object> root, String key) {
        Object val = root.get(key);
        if (val instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        root.put(key, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object val) {
        if (val instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return new LinkedHashMap<>();
    }

    private static Object nested(Map<String, Object> root, String... keys) {
        Object cur = root;
        for (String key : keys) {
            if (!(cur instanceof Map<?, ?> map)) {
                return null;
            }
            cur = map.get(key);
        }
        return cur;
    }

    private static boolean nestedBool(Map<String, Object> root, String section, String key) {
        Object val = nested(root, section, key);
        return bool(val, false);
    }

    private static int intVal(Object val, int fallback) {
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(String.valueOf(val));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static String str(Object val, String fallback) {
        if (val == null) {
            return fallback;
        }
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? fallback : s;
    }

    private static boolean bool(Object val, boolean fallback) {
        if (val instanceof Boolean b) {
            return b;
        }
        if (val != null) {
            return Boolean.parseBoolean(String.valueOf(val));
        }
        return fallback;
    }

    private static boolean jarPresent(Path pluginsDir, String token) {
        if (!Files.isDirectory(pluginsDir)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                if (jar.getFileName().toString().toLowerCase(Locale.ROOT).contains(token)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }
}

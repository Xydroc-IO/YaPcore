package com.yapcore.web;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads/writes {@code plugins/YaPEssentials/config.yml} for the web dashboard. */
public final class DashboardEssentialsSnapshot {

    private static final String DATA_DIR = "YaPEssentials";
    private static final List<String> FEATURE_KEYS = List.of(
            "spawn", "back", "tpa", "teleport", "fly", "god", "speed", "heal", "feed",
            "repair", "clear", "vanish", "invsee", "echest", "nick", "afk", "list",
            "ptime", "pweather", "weather", "broadcast", "rules", "motd", "suicide", "hat", "staff");

    private DashboardEssentialsSnapshot() {
    }

    public static boolean installed(Path pluginsDir) {
        return jarPresent(pluginsDir, "yap-essentials");
    }

    public static Map<String, Object> snapshot(Path rootDir) {
        Map<String, Object> out = new LinkedHashMap<>();
        Path pluginsDir = rootDir.resolve("plugins");
        out.put("installed", installed(pluginsDir));
        Path configFile = pluginsDir.resolve(DATA_DIR).resolve("config.yml");
        out.put("configPresent", Files.isRegularFile(configFile));
        if (!Files.isRegularFile(configFile)) {
            out.put("features", defaultFeatures());
            out.put("motd", List.of());
            out.put("rules", List.of());
            return out;
        }
        try {
            Map<String, Object> yaml = loadYaml(configFile);
            out.put("serverId", str(yaml.get("server-id"), "default"));
            out.put("useSharedYapdb", bool(yaml.get("use-shared-yapdb"), true));
            out.put("spawn", spawnSnapshot(yaml));
            out.put("features", featuresSnapshot(yaml));
            out.put("motd", stringList(nested(yaml, "messages", "motd")));
            out.put("rules", stringList(nested(yaml, "messages", "rules")));
        } catch (IOException e) {
            out.put("error", e.getMessage() == null ? "config read failed" : e.getMessage());
        }
        return out;
    }

    public static void saveFeature(Path rootDir, String key, boolean enabled) throws IOException {
        Path configFile = rootDir.resolve("plugins").resolve(DATA_DIR).resolve("config.yml");
        Map<String, Object> yaml = loadYaml(configFile);
        Map<String, Object> features = mapOrCreate(yaml, "features");
        features.put(key, enabled);
        dumpYaml(configFile, yaml);
    }

    public static void saveMotd(Path rootDir, List<String> lines) throws IOException {
        Path configFile = rootDir.resolve("plugins").resolve(DATA_DIR).resolve("config.yml");
        Map<String, Object> yaml = loadYaml(configFile);
        Map<String, Object> messages = mapOrCreate(yaml, "messages");
        messages.put("motd", lines);
        dumpYaml(configFile, yaml);
    }

    public static void saveRules(Path rootDir, List<String> lines) throws IOException {
        Path configFile = rootDir.resolve("plugins").resolve(DATA_DIR).resolve("config.yml");
        Map<String, Object> yaml = loadYaml(configFile);
        Map<String, Object> messages = mapOrCreate(yaml, "messages");
        messages.put("rules", lines);
        dumpYaml(configFile, yaml);
    }

    private static Map<String, Object> spawnSnapshot(Map<String, Object> yaml) {
        Map<String, Object> spawn = map(yaml.get("spawn"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", str(spawn.get("world"), "world"));
        out.put("x", number(spawn.get("x"), 0.5));
        out.put("y", number(spawn.get("y"), 80.0));
        out.put("z", number(spawn.get("z"), 0.5));
        out.put("yaw", number(spawn.get("yaw"), 0.0));
        out.put("pitch", number(spawn.get("pitch"), 0.0));
        out.put("scope", str(spawn.get("scope"), "server"));
        out.put("persistDb", bool(spawn.get("persist-db"), true));
        return out;
    }

    private static Map<String, Boolean> featuresSnapshot(Map<String, Object> yaml) {
        Map<String, Object> features = map(yaml.get("features"));
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (String key : FEATURE_KEYS) {
            out.put(key, bool(features.get(key), true));
        }
        return out;
    }

    private static Map<String, Boolean> defaultFeatures() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (String key : FEATURE_KEYS) {
            out.put(key, true);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(file)) {
            Object loaded = yaml.load(in);
            if (loaded instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
        }
        return new LinkedHashMap<>();
    }

    private static void dumpYaml(Path file, Map<String, Object> data) throws IOException {
        Files.createDirectories(file.getParent());
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        opts.setIndent(2);
        Yaml yaml = new Yaml(opts);
        Files.writeString(file, yaml.dump(data), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOrCreate(Map<String, Object> root, String key) {
        Object val = root.get(key);
        if (val instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        root.put(key, created);
        return created;
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object val) {
        if (val instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static List<String> stringList(Object val) {
        List<String> out = new ArrayList<>();
        if (val instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
        }
        return out;
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

    private static double number(Object val, double fallback) {
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val != null) {
            try {
                return Double.parseDouble(String.valueOf(val));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean jarPresent(Path pluginsDir, String token) {
        if (!Files.isDirectory(pluginsDir)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.contains(token)) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}

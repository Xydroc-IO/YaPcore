package com.yapcore.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/** Properties I/O + Link plugin / selector / mod-sync snapshot helpers. */
final class DashboardLinkProps {

    private static final List<String> SUITE_IDS = List.of(
            "yaplink-chat-bridge", "yaplink-mod-sync", "yaplink-server-selector");

    private DashboardLinkProps() {
    }

    static List<String> suiteIds() {
        return SUITE_IDS;
    }

    static Properties load(Path file) {
        Properties props = new Properties();
        if (!Files.isRegularFile(file)) {
            return props;
        }
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException ignored) {
        }
        return props;
    }

    static void store(Path file, Properties props) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "YaP Link — edited via YaPcore dashboard");
        }
    }

    static boolean suiteComplete(Map<String, Object> snap) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plugins = (List<Map<String, Object>>) snap.get("plugins");
        if (plugins == null) {
            return false;
        }
        long matched = plugins.stream()
                .filter(p -> SUITE_IDS.contains(String.valueOf(p.get("id"))))
                .count();
        return matched >= SUITE_IDS.size();
    }

    static Map<String, Object> selectorSnapshot(Path home) {
        Path configFile = home.resolve("plugins").resolve("yaplink-server-selector").resolve("config.properties");
        Properties props = load(configFile);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configPresent", Files.isRegularFile(configFile));
        out.put("hubServer", props.getProperty("hub-server", "lobby"));
        out.put("sessionLockEnabled", Boolean.parseBoolean(props.getProperty("session-lock-enabled", "true")));
        out.put("jdbcUrl", maskJdbc(props.getProperty("jdbc-url", "")));
        return out;
    }

    static Map<String, Object> modSyncSnapshot(Path home) {
        Path configFile = home.resolve("plugins").resolve("yaplink-mod-sync").resolve("config.properties");
        Properties props = load(configFile);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configPresent", Files.isRegularFile(configFile));
        String jdbc = props.getProperty("jdbc-url", "");
        out.put("jdbcConfigured", jdbc != null && !jdbc.isBlank());
        out.put("jdbcUrl", maskJdbc(jdbc));
        return out;
    }

    static String maskJdbc(String jdbc) {
        if (jdbc == null || jdbc.isBlank()) {
            return "—";
        }
        return jdbc.replaceAll("password=[^&]*", "password=***");
    }

    static List<Map<String, Object>> scanPlugins(Path home) {
        Path dir = home.resolve("plugins");
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".jar"))
                    .sorted()
                    .forEach(jar -> {
                        Map<String, Object> meta = readLinkPluginMeta(jar);
                        if (meta != null) {
                            out.add(meta);
                        } else {
                            Map<String, Object> fallback = new LinkedHashMap<>();
                            fallback.put("id", jar.getFileName().toString());
                            fallback.put("name", jar.getFileName().toString());
                            fallback.put("version", "—");
                            fallback.put("jar", jar.getFileName().toString());
                            fallback.put("suite", false);
                            out.add(fallback);
                        }
                    });
        } catch (IOException ignored) {
        }
        return out;
    }

    private static Map<String, Object> readLinkPluginMeta(Path jar) {
        try (JarFile file = new JarFile(jar.toFile())) {
            ZipEntry entry = file.getJarEntry("link-plugin.json");
            if (entry == null) {
                return null;
            }
            try (InputStream in = file.getInputStream(entry)) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                Map<String, Object> meta = new LinkedHashMap<>();
                String id = root.get("id").getAsString();
                meta.put("id", id);
                meta.put("name", root.has("name") ? root.get("name").getAsString() : id);
                meta.put("version", root.has("version") ? root.get("version").getAsString() : "1.0.0");
                meta.put("jar", jar.getFileName().toString());
                meta.put("suite", SUITE_IDS.contains(id));
                return meta;
            }
        } catch (Exception e) {
            return null;
        }
    }
}

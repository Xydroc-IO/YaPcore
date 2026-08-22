package com.yapcore.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
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

/** Reads/writes {@code link-data/} for the web dashboard (Phase 5 Link suite). */
public final class DashboardLinkSnapshot {

    private static final List<String> SUITE_IDS = List.of(
            "yaplink-chat-bridge", "yaplink-mod-sync", "yaplink-server-selector");

    private DashboardLinkSnapshot() {
    }

    public static Path resolveHome(Path rootDir, String linkEmbedHome) {
        Path home = Path.of(linkEmbedHome == null || linkEmbedHome.isBlank() ? "link-data" : linkEmbedHome.trim());
        if (home.isAbsolute()) {
            return home.normalize();
        }
        return rootDir.resolve(home).normalize();
    }

    public static Map<String, Object> snapshot(Path rootDir, String linkEmbedHome, boolean linkEmbed, boolean velocityEnabled) {
        Path home = resolveHome(rootDir, linkEmbedHome);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("linkHome", home.toString());
        out.put("linkHomeExists", Files.isDirectory(home));
        out.put("linkEmbed", linkEmbed);
        out.put("velocityEnabled", velocityEnabled);

        Path linkProps = home.resolve("link.properties");
        out.put("configPresent", Files.isRegularFile(linkProps));
        Properties props = loadProperties(linkProps);
        out.put("bind", props.getProperty("bind", "—"));
        out.put("motd", props.getProperty("motd", "YaP Link"));
        out.put("pluginsEnabled", Boolean.parseBoolean(props.getProperty("plugins-enabled", "false")));
        out.put("chatRelayEnabled", Boolean.parseBoolean(props.getProperty("chat-relay-enabled", "false")));
        out.put("chatRelayFormat", props.getProperty("chat-relay-format", ""));
        out.put("bedrockEnabled", Boolean.parseBoolean(props.getProperty("bedrock-enabled", "false")));
        out.put("servers", parseServers(props));
        out.put("tryServers", parseTry(props));
        out.put("plugins", scanPlugins(home));
        out.put("selector", selectorSnapshot(home));
        out.put("modSync", modSyncSnapshot(home));
        out.put("suiteComplete", suiteComplete(out));
        return out;
    }

    public static void saveSelectorConfig(Path rootDir, String linkEmbedHome, String hubServer, boolean sessionLock) throws IOException {
        Path configFile = resolveHome(rootDir, linkEmbedHome)
                .resolve("plugins")
                .resolve("yaplink-server-selector")
                .resolve("config.properties");
        Properties props = loadProperties(configFile);
        if (hubServer != null && !hubServer.isBlank()) {
            props.setProperty("hub-server", hubServer.trim());
        }
        props.setProperty("session-lock-enabled", Boolean.toString(sessionLock));
        storeProperties(configFile, props);
    }

    public static void saveLinkFlags(
            Path rootDir,
            String linkEmbedHome,
            Boolean pluginsEnabled,
            Boolean chatRelayEnabled
    ) throws IOException {
        Path linkProps = resolveHome(rootDir, linkEmbedHome).resolve("link.properties");
        Properties props = loadProperties(linkProps);
        if (pluginsEnabled != null) {
            props.setProperty("plugins-enabled", Boolean.toString(pluginsEnabled));
        }
        if (chatRelayEnabled != null) {
            props.setProperty("chat-relay-enabled", Boolean.toString(chatRelayEnabled));
        }
        storeProperties(linkProps, props);
    }

    private static boolean suiteComplete(Map<String, Object> snap) {
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

    private static Map<String, Object> selectorSnapshot(Path home) {
        Path configFile = home.resolve("plugins").resolve("yaplink-server-selector").resolve("config.properties");
        Properties props = loadProperties(configFile);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configPresent", Files.isRegularFile(configFile));
        out.put("hubServer", props.getProperty("hub-server", "lobby"));
        out.put("sessionLockEnabled", Boolean.parseBoolean(props.getProperty("session-lock-enabled", "true")));
        out.put("jdbcUrl", maskJdbc(props.getProperty("jdbc-url", "")));
        return out;
    }

    private static Map<String, Object> modSyncSnapshot(Path home) {
        Path configFile = home.resolve("plugins").resolve("yaplink-mod-sync").resolve("config.properties");
        Properties props = loadProperties(configFile);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configPresent", Files.isRegularFile(configFile));
        String jdbc = props.getProperty("jdbc-url", "");
        out.put("jdbcConfigured", jdbc != null && !jdbc.isBlank());
        out.put("jdbcUrl", maskJdbc(jdbc));
        return out;
    }

    private static String maskJdbc(String jdbc) {
        if (jdbc == null || jdbc.isBlank()) {
            return "—";
        }
        return jdbc.replaceAll("password=[^&]*", "password=***");
    }

    private static List<Map<String, Object>> parseServers(Properties props) {
        List<Map<String, Object>> servers = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("servers.")) {
                continue;
            }
            String rest = key.substring("servers.".length());
            if (rest.contains(".")) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", rest);
            entry.put("address", props.getProperty(key, ""));
            servers.add(entry);
        }
        servers.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
        return servers;
    }

    private static List<String> parseTry(Properties props) {
        String raw = props.getProperty("try", "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static List<Map<String, Object>> scanPlugins(Path home) {
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

    private static Properties loadProperties(Path file) {
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

    private static void storeProperties(Path file, Properties props) throws IOException {
        Files.createDirectories(file.getParent());
        StringBuilder sb = new StringBuilder();
        for (String key : props.stringPropertyNames().stream().sorted().toList()) {
            sb.append(key).append('=').append(props.getProperty(key)).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }
}

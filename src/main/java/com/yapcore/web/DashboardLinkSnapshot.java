package com.yapcore.web;

import com.google.gson.JsonElement;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/** Reads/writes {@code link-data/} for the web dashboard (Phase 5 Link suite). */
public final class DashboardLinkSnapshot {

    private static final List<String> SUITE_IDS = List.of(
            "yaplink-chat-bridge", "yaplink-mod-sync", "yaplink-server-selector");

    private static final Pattern SERVER_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]*");
    private static final Pattern HOST_PORT = Pattern.compile(".+:\\d{1,5}");

    /** Keys the dashboard may update via {@link #saveProxySettings}. */
    private static final Set<String> PROXY_KEYS = Set.of(
            "bind", "motd", "max-players", "online-mode", "public-host", "public-port",
            "ping-passthrough", "aggregate-player-count", "global-tab-list",
            "chat-relay-enabled", "chat-relay-channel", "chat-relay-format", "chat-join-announce",
            "plugins-enabled", "enable-server-command",
            "bedrock-enabled", "bedrock-bind", "bedrock-backend", "floodgate-key-file",
            "connect-timeout-ms", "login-timeout-ms", "read-timeout-sec",
            "backend-probe-interval-sec", "backend-probe-timeout-ms", "skip-down-on-forced-host");

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
        out.put("bind", props.getProperty("bind", "0.0.0.0:25565"));
        out.put("motd", props.getProperty("motd", "YaP Link"));
        out.put("maxPlayers", props.getProperty("max-players", "500"));
        out.put("onlineMode", Boolean.parseBoolean(props.getProperty("online-mode", "false")));
        out.put("publicHost", props.getProperty("public-host", "127.0.0.1"));
        out.put("publicPort", props.getProperty("public-port", "0"));
        out.put("pluginsEnabled", Boolean.parseBoolean(props.getProperty("plugins-enabled", "false")));
        out.put("chatRelayEnabled", Boolean.parseBoolean(props.getProperty("chat-relay-enabled", "false")));
        out.put("chatRelayChannel", props.getProperty("chat-relay-channel", "network"));
        out.put("chatRelayFormat", props.getProperty("chat-relay-format", "[{server}] {name}: {message}"));
        out.put("chatJoinAnnounce", Boolean.parseBoolean(props.getProperty("chat-join-announce", "false")));
        out.put("aggregatePlayerCount", Boolean.parseBoolean(props.getProperty("aggregate-player-count", "true")));
        out.put("globalTabList", Boolean.parseBoolean(props.getProperty("global-tab-list", "false")));
        out.put("pingPassthrough", Boolean.parseBoolean(props.getProperty("ping-passthrough", "true")));
        out.put("enableServerCommand", Boolean.parseBoolean(props.getProperty("enable-server-command", "true")));
        out.put("bedrockEnabled", Boolean.parseBoolean(props.getProperty("bedrock-enabled", "false")));
        out.put("bedrockBind", props.getProperty("bedrock-bind", "0.0.0.0:19132"));
        out.put("bedrockBackend", props.getProperty("bedrock-backend", "127.0.0.1:25566"));
        out.put("floodgateKeyFile", props.getProperty("floodgate-key-file", "floodgate-key.pem"));
        out.put("connectTimeoutMs", props.getProperty("connect-timeout-ms", "10000"));
        out.put("loginTimeoutMs", props.getProperty("login-timeout-ms", "30000"));
        out.put("servers", parseServers(props));
        out.put("tryServers", parseTry(props));
        out.put("forcedHosts", parseForcedHosts(props));
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
        Map<String, String> updates = new LinkedHashMap<>();
        if (pluginsEnabled != null) {
            updates.put("plugins-enabled", Boolean.toString(pluginsEnabled));
        }
        if (chatRelayEnabled != null) {
            updates.put("chat-relay-enabled", Boolean.toString(chatRelayEnabled));
        }
        saveProxySettings(rootDir, linkEmbedHome, updates);
    }

    /** Updates whitelisted {@code link.properties} keys (preserves servers / forced-host). */
    public static void saveProxySettings(Path rootDir, String linkEmbedHome, Map<String, String> updates) throws IOException {
        Path linkProps = resolveHome(rootDir, linkEmbedHome).resolve("link.properties");
        Properties props = loadProperties(linkProps);
        for (var e : updates.entrySet()) {
            if (PROXY_KEYS.contains(e.getKey()) && e.getValue() != null) {
                props.setProperty(e.getKey(), e.getValue().trim());
            }
        }
        storeProperties(linkProps, props);
    }

    /** Replaces all {@code servers.*} and {@code forced-host.*} keys. */
    public static void saveServersConfig(
            Path rootDir,
            String linkEmbedHome,
            List<Map<String, String>> servers,
            List<String> tryOrder,
            List<Map<String, String>> forcedHosts
    ) throws IOException {
        if (servers == null || servers.isEmpty()) {
            throw new IOException("At least one backend server is required");
        }
        Path linkProps = resolveHome(rootDir, linkEmbedHome).resolve("link.properties");
        Properties props = loadProperties(linkProps);
        List<String> remove = props.stringPropertyNames().stream()
                .filter(k -> k.startsWith("servers.") || k.startsWith("forced-host."))
                .toList();
        for (String key : remove) {
            props.remove(key);
        }

        Set<String> names = new LinkedHashSet<>();
        for (Map<String, String> server : servers) {
            String name = normalizeServerName(server.get("name"));
            String address = normalizeAddress(server.get("address"));
            if (!names.add(name)) {
                throw new IOException("Duplicate server name: " + name);
            }
            props.setProperty("servers." + name, address);
            String bedrock = server.get("bedrock");
            if (bedrock != null && !bedrock.isBlank()) {
                props.setProperty("servers." + name + ".bedrock", normalizeAddress(bedrock));
            }
        }

        List<String> tryList = new ArrayList<>();
        if (tryOrder == null || tryOrder.isEmpty()) {
            tryList.addAll(names);
        } else {
            for (String raw : tryOrder) {
                tryList.add(normalizeServerName(raw));
            }
        }
        for (String name : tryList) {
            if (!names.contains(name)) {
                throw new IOException("try order references unknown server: " + name);
            }
        }
        props.setProperty("try", String.join(",", tryList));

        if (forcedHosts != null) {
            for (Map<String, String> entry : forcedHosts) {
                String host = entry.get("host");
                if (host == null || host.isBlank()) {
                    continue;
                }
                String target = normalizeServerName(entry.get("server"));
                if (!names.contains(target)) {
                    throw new IOException("forced-host targets unknown server: " + target);
                }
                props.setProperty("forced-host." + host.trim().toLowerCase(Locale.ROOT), target);
            }
        }
        storeProperties(linkProps, props);
    }

    /** Parses POST body for {@code save-servers} (Gson). */
    public static void saveServersFromJson(Path rootDir, String linkEmbedHome, String jsonBody) throws IOException {
        JsonObject root = JsonParser.parseString(jsonBody == null ? "{}" : jsonBody).getAsJsonObject();
        List<Map<String, String>> servers = parseServerArray(root.get("servers"));
        List<String> tryOrder = parseStringArray(root.get("try"));
        List<Map<String, String>> forced = parseForcedArray(root.get("forcedHosts"));
        saveServersConfig(rootDir, linkEmbedHome, servers, tryOrder, forced);
    }

    private static List<Map<String, String>> parseServerArray(JsonElement el) {
        List<Map<String, String>> out = new ArrayList<>();
        if (el == null || !el.isJsonArray()) {
            return out;
        }
        for (JsonElement item : el.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject o = item.getAsJsonObject();
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", jsonString(o, "name"));
            row.put("address", jsonString(o, "address"));
            row.put("bedrock", jsonString(o, "bedrock"));
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, String>> parseForcedArray(JsonElement el) {
        List<Map<String, String>> out = new ArrayList<>();
        if (el == null || !el.isJsonArray()) {
            return out;
        }
        for (JsonElement item : el.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject o = item.getAsJsonObject();
            Map<String, String> row = new LinkedHashMap<>();
            row.put("host", jsonString(o, "host"));
            row.put("server", jsonString(o, "server"));
            out.add(row);
        }
        return out;
    }

    private static List<String> parseStringArray(JsonElement el) {
        List<String> out = new ArrayList<>();
        if (el == null || !el.isJsonArray()) {
            return out;
        }
        for (JsonElement item : el.getAsJsonArray()) {
            if (item.isJsonPrimitive()) {
                String s = item.getAsString().trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static String jsonString(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        return o.get(key).getAsString();
    }

    private static String normalizeServerName(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            throw new IOException("Server name required");
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (!SERVER_NAME.matcher(name).matches()) {
            throw new IOException("Invalid server name: " + raw);
        }
        return name;
    }

    private static String normalizeAddress(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            throw new IOException("host:port address required");
        }
        String address = raw.trim();
        if (!HOST_PORT.matcher(address).matches()) {
            throw new IOException("Invalid address (expected host:port): " + raw);
        }
        return address;
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
            String bedrock = props.getProperty("servers." + rest + ".bedrock", "");
            if (bedrock != null && !bedrock.isBlank()) {
                entry.put("bedrock", bedrock);
            }
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

    private static List<Map<String, Object>> parseForcedHosts(Properties props) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("forced-host.")) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("host", key.substring("forced-host.".length()));
            entry.put("server", props.getProperty(key, ""));
            out.add(entry);
        }
        out.sort((a, b) -> String.valueOf(a.get("host")).compareToIgnoreCase(String.valueOf(b.get("host"))));
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
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "YaP Link — edited via YaPcore dashboard");
        }
    }
}

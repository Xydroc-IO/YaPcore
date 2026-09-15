package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Reads/writes {@code link-data/} for the web dashboard (Phase 5 Link suite). */
public final class DashboardLinkSnapshot {

    /**
     * Keys the dashboard may update via {@link #saveProxySettings}.
     * MOTD / online-mode / public-host / public-port are chassis-owned
     * ({@code LinkIdentityMirror}) — not writable here. {@code max-players} is
     * Link-owned (network-wide cap).
     */
    private static final Set<String> PROXY_KEYS = Set.of(
            "bind", "max-players",
            "ping-passthrough", "aggregate-player-count", "global-tab-list",
            "chat-relay-enabled", "chat-relay-channel", "chat-relay-format", "chat-join-announce",
            "plugins-enabled", "enable-server-command",
            "bedrock-enabled", "bedrock-bind", "bedrock-backend", "bedrock-mode", "floodgate-key-file",
            "geyser-enabled", "geyser-home", "geyser-jar", "geyser-remote-host", "geyser-remote-port",
            "geyser-bedrock-bind",
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
        Properties props = DashboardLinkProps.load(linkProps);
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
        out.put("bedrockMode", props.getProperty("bedrock-mode", "native"));
        out.put("bedrockBind", props.getProperty("bedrock-bind", "0.0.0.0:19132"));
        out.put("bedrockBackend", props.getProperty("bedrock-backend", "127.0.0.1:25566"));
        out.put("floodgateKeyFile", props.getProperty("floodgate-key-file", "floodgate-key.pem"));
        out.put("geyserEnabled", Boolean.parseBoolean(props.getProperty("geyser-enabled", "false")));
        Path geyserJar = home.resolve(props.getProperty("geyser-home", "geyser"))
                .resolve(props.getProperty("geyser-jar", "Geyser-Standalone.jar"));
        out.put("geyserJar", geyserJar.toString());
        out.put("geyserJarPresent", Files.isRegularFile(geyserJar));
        out.put("connectTimeoutMs", props.getProperty("connect-timeout-ms", "10000"));
        out.put("loginTimeoutMs", props.getProperty("login-timeout-ms", "30000"));
        out.put("servers", DashboardLinkServers.parseServers(props));
        out.put("tryServers", DashboardLinkServers.parseTry(props));
        out.put("forcedHosts", DashboardLinkServers.parseForcedHosts(props));
        out.put("plugins", DashboardLinkProps.scanPlugins(home));
        out.put("selector", DashboardLinkProps.selectorSnapshot(home));
        out.put("modSync", DashboardLinkProps.modSyncSnapshot(home));
        out.put("suiteComplete", DashboardLinkProps.suiteComplete(out));
        return out;
    }

    public static void saveSelectorConfig(Path rootDir, String linkEmbedHome, String hubServer, boolean sessionLock) throws IOException {
        Path configFile = resolveHome(rootDir, linkEmbedHome)
                .resolve("plugins")
                .resolve("yaplink-server-selector")
                .resolve("config.properties");
        Properties props = DashboardLinkProps.load(configFile);
        if (hubServer != null && !hubServer.isBlank()) {
            props.setProperty("hub-server", hubServer.trim());
        }
        props.setProperty("session-lock-enabled", Boolean.toString(sessionLock));
        DashboardLinkProps.store(configFile, props);
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
        Properties props = DashboardLinkProps.load(linkProps);
        for (var e : updates.entrySet()) {
            if (PROXY_KEYS.contains(e.getKey()) && e.getValue() != null) {
                props.setProperty(e.getKey(), e.getValue().trim());
            }
        }
        DashboardLinkProps.store(linkProps, props);
    }

    /**
     * Whether Bedrock clients can join via the Link edge (native UDP or Geyser backup).
     * Chassis {@code bedrock-enabled} is unrelated in {@code native} mode — Link owns UDP.
     */
    public static boolean allowBedrockPlayers(Path rootDir, String linkEmbedHome) {
        Properties props = DashboardLinkProps.load(resolveHome(rootDir, linkEmbedHome).resolve("link.properties"));
        String mode = props.getProperty("bedrock-mode", "native").trim().toLowerCase(Locale.ROOT);
        if ("geyser-backup".equals(mode) || "geyser".equals(mode) || "backup".equals(mode)) {
            return Boolean.parseBoolean(props.getProperty("geyser-enabled", "false"));
        }
        // native + forwarder: Link Bedrock edge
        if (!Files.isRegularFile(resolveHome(rootDir, linkEmbedHome).resolve("link.properties"))) {
            return true; // product default before first write
        }
        return Boolean.parseBoolean(props.getProperty("bedrock-enabled", "true"));
    }

    /**
     * Turn Link-edge Bedrock on/off without changing chassis Folia bind.
     * Ensures UDP binds exist when enabling native/forwarder.
     */
    public static void setAllowBedrockPlayers(Path rootDir, String linkEmbedHome, boolean allow) throws IOException {
        Path linkProps = resolveHome(rootDir, linkEmbedHome).resolve("link.properties");
        Files.createDirectories(linkProps.getParent());
        Properties props = DashboardLinkProps.load(linkProps);
        String mode = props.getProperty("bedrock-mode", "native").trim().toLowerCase(Locale.ROOT);
        boolean geyser = "geyser-backup".equals(mode) || "geyser".equals(mode) || "backup".equals(mode);
        if (geyser) {
            props.setProperty("geyser-enabled", Boolean.toString(allow));
            props.setProperty("bedrock-enabled", "false");
            if (allow) {
                if (props.getProperty("geyser-bedrock-bind", "").isBlank()) {
                    props.setProperty("geyser-bedrock-bind", "0.0.0.0:19132");
                }
            }
        } else {
            props.setProperty("bedrock-enabled", Boolean.toString(allow));
            props.setProperty("geyser-enabled", "false");
            if (allow) {
                String bind = props.getProperty("bedrock-bind", "");
                if (bind.isBlank()) {
                    props.setProperty("bedrock-bind", "0.0.0.0:25565,0.0.0.0:19132");
                }
                props.setProperty("bedrock-shared-port", "true");
                props.setProperty("bedrock-also-19132", "true");
            }
        }
        DashboardLinkProps.store(linkProps, props);
    }

    /** Replaces all {@code servers.*} and {@code forced-host.*} keys. */
    public static void saveServersConfig(
            Path rootDir,
            String linkEmbedHome,
            List<Map<String, String>> servers,
            List<String> tryOrder,
            List<Map<String, String>> forcedHosts
    ) throws IOException {
        DashboardLinkServers.saveServersConfig(rootDir, linkEmbedHome, servers, tryOrder, forcedHosts);
    }

    /** Parses POST body for {@code save-servers} (Gson). */
    public static void saveServersFromJson(Path rootDir, String linkEmbedHome, String jsonBody) throws IOException {
        DashboardLinkServers.saveServersFromJson(rootDir, linkEmbedHome, jsonBody);
    }
}

package com.yapcore.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Keeps YaP Link's player-facing identity in lockstep with chassis
 * {@code config/server.properties} so Java and Bedrock joiners see the same
 * MOTD, player cap, auth mode, and public host for one shared world.
 */
public final class LinkIdentityMirror {

    private LinkIdentityMirror() {}

    /**
     * Push shared identity from product config into {@code link.properties}.
     * Creates the file if missing. Does not start/stop Link.
     */
    public static void syncFromServerConfig(Path rootDir, ServerConfig config) throws IOException {
        Path linkHome = resolveLinkHome(rootDir, config.getLinkEmbedHome());
        Files.createDirectories(linkHome);
        Path linkPropsFile = linkHome.resolve("link.properties");
        Properties link = loadProps(linkPropsFile);

        link.setProperty("motd", config.getMotd());
        link.setProperty("max-players", Integer.toString(config.getMaxPlayers()));
        link.setProperty("online-mode", Boolean.toString(config.isOnlineMode()));
        if (config.getPublicHost() != null && !config.getPublicHost().isBlank()) {
            link.setProperty("public-host", config.getPublicHost().trim());
        }
        link.setProperty("public-port", Integer.toString(Math.max(0, config.getPublicPort())));

        // Product default: Link owns Bedrock UDP — keep edge enabled so list ping works.
        String mode = BedrockModeApplier.normalize(config.getBedrockMode());
        link.setProperty("bedrock-mode", mode);
        if (BedrockModeApplier.isNative(mode)) {
            link.setProperty("bedrock-enabled", "true");
            link.setProperty("geyser-enabled", "false");
            String bind = link.getProperty("bedrock-bind", "");
            if (bind.isBlank() || "0.0.0.0:19132".equals(bind.trim())) {
                link.setProperty("bedrock-bind", "0.0.0.0:25565,0.0.0.0:19132");
                link.setProperty("bedrock-shared-port", "true");
                link.setProperty("bedrock-also-19132", "true");
            }
        } else if (BedrockModeApplier.isGeyserBackup(mode)) {
            link.setProperty("bedrock-enabled", "false");
            link.setProperty("geyser-enabled", "true");
        }

        // Folia/Paper backend listen port — keep Link lobby + Bedrock backend aligned.
        int backendPort = config.getPort();
        String lobby = link.getProperty("servers.lobby", "");
        if (lobby.isBlank() || looksLikeLocalBackend(lobby)) {
            link.setProperty("servers.lobby", "127.0.0.1:" + backendPort);
        }
        String bedrockBackend = link.getProperty("bedrock-backend", "");
        if (bedrockBackend.isBlank() || looksLikeLocalBackend(bedrockBackend)) {
            link.setProperty("bedrock-backend", "127.0.0.1:" + backendPort);
        }

        storeProps(linkPropsFile, link);
    }

    private static boolean looksLikeLocalBackend(String address) {
        String a = address.trim().toLowerCase();
        return a.startsWith("127.0.0.1:") || a.startsWith("localhost:");
    }

    private static Path resolveLinkHome(Path rootDir, String linkEmbedHome) {
        Path home = Path.of(linkEmbedHome == null || linkEmbedHome.isBlank() ? "link-data" : linkEmbedHome.trim());
        if (home.isAbsolute()) {
            return home.normalize();
        }
        return rootDir.resolve(home).normalize();
    }

    private static Properties loadProps(Path file) throws IOException {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
        }
        return p;
    }

    private static void storeProps(Path file, Properties props) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "YaP Link — identity mirrored from config/server.properties (JE+BE shared world)");
        }
    }
}

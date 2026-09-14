package com.yapcore.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Keeps YaP Link's shared world identity in lockstep with chassis
 * {@code config/server.properties} so Java and Bedrock list pings show the same MOTD.
 *
 * <p>{@code max-players} is intentionally not mirrored: Link owns the network-wide
 * cap (sum of backends / headroom), while each Folia backend keeps its own slot limit.
 */
public final class LinkIdentityMirror {

    private LinkIdentityMirror() {}

    /**
     * Push shared identity from product config into {@code link.properties}.
     * Creates the file if missing. Does not start/stop Link.
     *
     * @return {@code true} if MOTD or auth/public identity fields changed (caller may reload Link)
     */
    public static boolean syncFromServerConfig(Path rootDir, ServerConfig config) throws IOException {
        Path linkHome = resolveLinkHome(rootDir, config.getLinkEmbedHome());
        Files.createDirectories(linkHome);
        Path linkPropsFile = linkHome.resolve("link.properties");
        Properties link = loadProps(linkPropsFile);

        String motd = config.getMotd() == null ? "" : config.getMotd();
        String online = Boolean.toString(config.isOnlineMode());
        String publicPort = Integer.toString(Math.max(0, config.getPublicPort()));
        String publicHost = config.getPublicHost() == null ? "" : config.getPublicHost().trim();

        boolean identityChanged =
                !motd.equals(link.getProperty("motd", ""))
                        || !online.equals(link.getProperty("online-mode", "false"))
                        || !publicPort.equals(link.getProperty("public-port", "0"))
                        || (!publicHost.isBlank()
                        && !publicHost.equals(link.getProperty("public-host", "")));

        // Chassis MOTD is the single source for JE + BE server-list text.
        link.setProperty("motd", motd);
        // Do not touch max-players — network-wide cap lives only on Link.
        link.setProperty("online-mode", online);
        if (!publicHost.isBlank()) {
            link.setProperty("public-host", publicHost);
        }
        link.setProperty("public-port", publicPort);

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

        // Fleet owns per-instance backends (LinkFleetSync). Never stomp servers.* / bedrock-backend
        // back to the chassis Via/listen port — that made every game server look like :25566.
        if (!config.isFleetEnabled()) {
            int backendPort = config.foliaListenPort();
            String lobby = link.getProperty("servers.lobby", "");
            if (lobby.isBlank()) {
                link.setProperty("servers.lobby", "127.0.0.1:" + backendPort);
            }
            String bedrockBackend = link.getProperty("bedrock-backend", "");
            if (bedrockBackend.isBlank()) {
                link.setProperty("bedrock-backend", "127.0.0.1:" + backendPort);
            }
        }

        storeProps(linkPropsFile, link);
        return identityChanged;
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

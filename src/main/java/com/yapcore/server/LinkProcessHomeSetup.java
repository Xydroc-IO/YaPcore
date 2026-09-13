package com.yapcore.server;

import com.yapcore.config.ServerConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Home-directory seed helpers for {@link LinkProcessManager}
 * (split for the ≤500-line domain gate).
 */
final class LinkProcessHomeSetup {

    private LinkProcessHomeSetup() {
    }

    static void ensureForwardingSecret(Path rootDir, ServerConfig config, Path home) throws IOException {
        Path dest = home.resolve("forwarding.secret");
        if (Files.isRegularFile(dest)) {
            return;
        }
        String fileProp = config.getVelocitySecretFile();
        if (fileProp != null && !fileProp.isBlank()) {
            Path src = Path.of(fileProp);
            if (!src.isAbsolute()) {
                src = rootDir.resolve(fileProp);
            }
            if (Files.isRegularFile(src)) {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }
        Path rootSecret = rootDir.resolve("forwarding.secret");
        if (Files.isRegularFile(rootSecret)) {
            Files.copy(rootSecret, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void ensureLinkProperties(ServerConfig config, Path home) throws IOException {
        Path props = home.resolve("link.properties");
        if (Files.isRegularFile(props)) {
            return;
        }
        int backendPort = config.getPort();
        String motd = config.getMotd() == null || config.getMotd().isBlank()
                ? "YaPcore · Folia Game · Yap Edge" : config.getMotd();
        String content = """
                # YaP Link — seeded by YaPcore (identity mirrors config/server.properties)
                bind=0.0.0.0:25565
                motd=%s
                max-players=%d
                online-mode=%s
                player-info-forwarding-mode=modern
                forwarding-secret-file=forwarding.secret
                servers.lobby=127.0.0.1:%d
                try=lobby
                enable-server-command=true
                plugins-enabled=true
                ping-passthrough=true
chat-relay-enabled=true
bedrock-mode=native
bedrock-enabled=true
bedrock-bind=0.0.0.0:25565,0.0.0.0:19132
bedrock-shared-port=true
bedrock-also-19132=true
bedrock-backend=127.0.0.1:%d
geyser-enabled=false
geyser-home=geyser
geyser-jar=Geyser-Standalone.jar
""".formatted(
                motd.replace("\n", " ").replace("\r", ""),
                config.getMaxPlayers(),
                Boolean.toString(config.isOnlineMode()),
                backendPort,
                backendPort);
        Files.writeString(props, content);
    }
}

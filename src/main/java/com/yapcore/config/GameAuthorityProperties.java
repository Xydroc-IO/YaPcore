package com.yapcore.config;

import com.yapcore.folia.FoliaFiles;
import com.yapcore.paper.PaperFiles;

import java.io.IOException;
import java.nio.file.Path;

/** Push product {@code config/server.properties} fields into the active game kernel. */
public final class GameAuthorityProperties {

    private GameAuthorityProperties() {
    }

    public static void sync(Path rootDir, ServerConfig config) throws IOException {
        if (config.isFoliaAuthority()) {
            Path dir = rootDir.resolve(config.getFoliaDir()).toAbsolutePath().normalize();
            FoliaFiles.writeServerProperties(
                    rootDir,
                    dir,
                    config,
                    config.foliaListenPort(),
                    bindHost(config),
                    "YaPcore product config sync");
            return;
        }
        if (config.isPaperAuthority()) {
            Path dir = rootDir.resolve(config.getPaperDir()).toAbsolutePath().normalize();
            PaperFiles.writeServerProperties(
                    rootDir,
                    dir,
                    config,
                    config.paperListenPort(),
                    bindHost(config),
                    "YaPcore product config sync");
        }
    }

    private static String bindHost(ServerConfig config) {
        String bind = config.getBindHost();
        if (bind == null || bind.isBlank() || "0.0.0.0".equals(bind)) {
            return config.isFoliaEmbed() || config.isPaperEmbed() ? "" : "127.0.0.1";
        }
        return bind;
    }
}

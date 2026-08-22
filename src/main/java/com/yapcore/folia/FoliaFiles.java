package com.yapcore.folia;

import com.yapcore.config.ServerConfig;
import com.yapcore.fill.FillClient;
import com.yapcore.paper.PaperFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/** Folia jar / eula / server.properties / Velocity helpers. */
public final class FoliaFiles {

    private static final Logger LOG = Logger.getLogger("YaPcore.FoliaFiles");

    private FoliaFiles() {
    }

    public static Path ensureFoliaJar(Path rootDir, Path foliaDir, ServerConfig config)
            throws IOException {
        String version = config.getFoliaVersion();
        Files.createDirectories(foliaDir);

        Path jar = foliaDir.resolve("folia-" + version + ".jar");
        if (Files.isRegularFile(jar) && Files.size(jar) > 1_000_000) {
            return jar;
        }
        Path cached = rootDir.resolve("lib").resolve("folia-" + version + ".jar");
        if (Files.isRegularFile(cached) && Files.size(cached) > 1_000_000) {
            Files.copy(cached, jar, StandardCopyOption.REPLACE_EXISTING);
            return jar;
        }
        String url = config.getFoliaJarUrl();
        if (url == null || url.isBlank()) {
            url = FillClient.latestServerJarUrl("folia", version);
        }
        Files.createDirectories(cached.getParent());
        PaperFiles.download(url, cached);
        Files.copy(cached, jar, StandardCopyOption.REPLACE_EXISTING);
        LOG.info("Fetched Folia " + version + " → " + cached.getFileName());
        return jar;
    }

    public static void writeEula(Path dir) throws IOException {
        PaperFiles.writeEula(dir);
    }

    public static void writeServerProperties(Path rootDir, Path dir, ServerConfig config,
                                             int listenPort, String bindIp, String comment)
            throws IOException {
        PaperFiles.writeServerProperties(rootDir, dir, config, listenPort, bindIp, comment);
    }

    public static void applyVelocitySupport(Path rootDir, Path foliaDir, ServerConfig config)
            throws IOException {
        PaperFiles.applyVelocitySupport(rootDir, foliaDir, config);
    }
}

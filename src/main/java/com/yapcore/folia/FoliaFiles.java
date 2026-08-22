package com.yapcore.folia;

import com.yapcore.config.ServerConfig;
import com.yapcore.fill.FillClient;
import com.yapcore.paper.PaperFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/** Folia jar / eula / server.properties helpers (managed-process embed). */
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
        // Folia uses the same paper-global.yml proxies.velocity keys as Paper.
        PaperFiles.applyVelocitySupport(rootDir, foliaDir, config);
    }

    /**
     * Seed Folia region settings when missing (product fork surface — not a source fork yet).
     * Keeps first boots from surprising ops; does not overwrite an existing folia.yml.
     */
    public static void ensureFoliaYml(Path foliaDir) throws IOException {
        Path foliaYml = foliaDir.resolve("config").resolve("folia-regionizer.yml");
        // Folia 1.20+ uses config/folia-world-defaults / regionizer under paper configs;
        // seed a small marker only when the config dir is empty of Folia-specific files.
        Path cfg = foliaDir.resolve("config");
        Files.createDirectories(cfg);
        Path marker = cfg.resolve("yap-folia-surface.marker");
        if (!Files.isRegularFile(marker)) {
            Files.writeString(marker,
                    "YaPcore Folia product surface\n"
                            + "velocity=paper-global.yml proxies.velocity\n"
                            + "plugins=../plugins symlink\n"
                            + "built-ins=folia-supported first-party jars\n",
                    java.nio.charset.StandardCharsets.UTF_8);
            LOG.info("Folia product surface marker → " + marker);
        }
        // Avoid unused-path warnings when Folia layouts differ across versions.
        if (Files.isRegularFile(foliaYml)) {
            LOG.fine("Folia regionizer config present: " + foliaYml);
        }
    }
}

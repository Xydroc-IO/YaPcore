package com.yapcore.folia;

import com.yapcore.config.ServerConfig;
import com.yapcore.fill.FillClient;
import com.yapcore.paper.PaperFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.logging.Logger;

/** Folia jar / eula / server.properties / Velocity helpers. */
public final class FoliaFiles {

    private static final Logger LOG = Logger.getLogger("YaPcore.FoliaFiles");

    private FoliaFiles() {
    }

    /**
     * Resolve the Folia server jar into {@code foliaDir/folia-{version}.jar}.
     * <p>Order:
     * <ol>
     *   <li>{@code folia-jar-path} (absolute or root-relative)</li>
     *   <li>{@code lib/yap-folia-{version}.jar} when {@code folia-jar-source=build}</li>
     *   <li>{@code lib/folia-{version}.jar} cache</li>
     *   <li>Fill / {@code folia-jar-url} download</li>
     * </ol>
     */
    public static Path ensureFoliaJar(Path rootDir, Path foliaDir, ServerConfig config)
            throws IOException {
        String version = config.getFoliaVersion();
        Files.createDirectories(foliaDir);

        Path jar = foliaDir.resolve("folia-" + version + ".jar");
        Path lib = rootDir.resolve("lib");
        Files.createDirectories(lib);

        Path configured = resolveConfiguredPath(rootDir, config.getFoliaJarPath());
        if (configured != null) {
            copyIfNeeded(configured, jar, "folia-jar-path");
            return jar;
        }

        String source = normalizeSource(config.getFoliaJarSource());
        Path yapBuilt = lib.resolve("yap-folia-" + version + ".jar");
        Path cached = lib.resolve("folia-" + version + ".jar");

        if ("build".equals(source)) {
            if (isUsableJar(yapBuilt)) {
                copyIfNeeded(yapBuilt, jar, "yap-folia build");
                return jar;
            }
            LOG.warning("folia-jar-source=build but missing usable "
                    + yapBuilt + " — falling back to stock cache / Fill if available. "
                    + "Run: ./scripts/build-yap-folia.sh");
        } else if ("path".equals(source)) {
            throw new IOException("folia-jar-source=path requires folia-jar-path=");
        }

        if (isUsableJar(jar)) {
            return jar;
        }
        if (isUsableJar(yapBuilt) && ("build".equals(source) || "auto".equals(source))) {
            copyIfNeeded(yapBuilt, jar, "yap-folia");
            return jar;
        }
        if (isUsableJar(cached)) {
            copyIfNeeded(cached, jar, "lib cache");
            return jar;
        }

        if ("build".equals(source)) {
            throw new IOException("folia-jar-source=build but missing usable jar at "
                    + yapBuilt + " — run ./scripts/build-yap-folia.sh "
                    + "(or set folia-jar-source=fetch for stock Folia)");
        }

        String url = config.getFoliaJarUrl();
        if (url == null || url.isBlank()) {
            url = FillClient.latestServerJarUrl("folia", version);
        }
        PaperFiles.download(url, cached);
        copyIfNeeded(cached, jar, "Fill download");
        LOG.info("Fetched Folia " + version + " → " + cached.getFileName());
        return jar;
    }

    private static Path resolveConfiguredPath(Path rootDir, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Path p = Path.of(raw.trim());
        if (!p.isAbsolute()) {
            p = rootDir.resolve(p).normalize();
        }
        return isUsableJar(p) ? p : null;
    }

    private static void copyIfNeeded(Path from, Path to, String why) throws IOException {
        if (Files.isRegularFile(to)
                && Files.size(to) == Files.size(from)
                && Files.getLastModifiedTime(to).equals(Files.getLastModifiedTime(from))) {
            return;
        }
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        LOG.info("Folia jar ← " + from.getFileName() + " (" + why + ")");
    }

    private static boolean isUsableJar(Path jar) {
        try {
            return Files.isRegularFile(jar) && Files.size(jar) > 1_000_000;
        } catch (IOException e) {
            return false;
        }
    }

    private static String normalizeSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return "fetch";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
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

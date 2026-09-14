package com.yapcore.fleet.local;

import com.yapcore.config.ServerConfig;
import com.yapcore.folia.FoliaFiles;
import com.yapcore.folia.surface.FoliaSurface;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.paper.PaperOps;
import com.yapcore.paper.PaperPluginsLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Creates / seeds {@code fleet/instances/{id}} layout for a Folia JVM. */
public final class InstanceLayout {

    private static final Logger LOG = Logger.getLogger("YaP.Fleet.Layout");

    private InstanceLayout() {
    }

    public static Path dir(Path rootDir, FleetInstance instance) {
        return rootDir.resolve(instance.relativeDir()).toAbsolutePath().normalize();
    }

    public static void ensure(Path rootDir, ServerConfig config, FleetInstance instance)
            throws IOException {
        Path dir = dir(rootDir, instance);
        Files.createDirectories(dir);
        PaperPluginsLayout.ensureUnified(rootDir, dir);
        seedPluginsFromRoot(rootDir, dir);
        Path jar = FoliaFiles.ensureFoliaJar(rootDir, dir, config);
        FoliaFiles.writeEula(dir);
        String bind = instance.bind();
        if ("0.0.0.0".equals(bind)) {
            bind = "";
        }
        FoliaFiles.writeServerProperties(
                rootDir,
                dir,
                config,
                instance.port(),
                bind,
                "YaP fleet instance " + instance.id() + " server-id=" + instance.serverId());
        FoliaFiles.applyVelocitySupport(rootDir, dir, config);
        FoliaSurface.ensureMarker(dir);
        PaperOps.ensure(dir, config);
        writeServerIdHint(dir, instance.serverId());
        LOG.info("Prepared fleet instance " + instance.id() + " jar=" + jar.getFileName()
                + " port=" + instance.port());
    }

    /**
     * Seed CORE+NETWORK jars from root catalog when missing; size-sync jars already present.
     * Does not auto-install gameplay/third-party jars.
     */
    public static void seedPluginsFromRoot(Path rootDir, Path instanceDir) throws IOException {
        Path rootPlugins = rootDir.resolve("plugins");
        Path localPlugins = instanceDir.resolve("plugins");
        if (!Files.isDirectory(rootPlugins) || Files.isSymbolicLink(localPlugins)) {
            return;
        }
        Files.createDirectories(localPlugins);
        int copied = 0;
        for (String name : FleetDefaultPlugins.coreNetworkJars()) {
            Path src = rootPlugins.resolve(name);
            if (!Files.isRegularFile(src)) {
                continue;
            }
            Path dest = localPlugins.resolve(name);
            if (Files.isRegularFile(dest) && Files.size(dest) == Files.size(src)) {
                continue;
            }
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            copied++;
        }
        // Size-sync any non-default jars already on the instance (operator installs)
        try (Stream<Path> existing = Files.list(localPlugins)) {
            for (Path dest : existing.toList()) {
                if (!Files.isRegularFile(dest) || !dest.getFileName().toString().endsWith(".jar")) {
                    continue;
                }
                Path src = rootPlugins.resolve(dest.getFileName().toString());
                if (!Files.isRegularFile(src)) {
                    continue;
                }
                if (Files.size(dest) != Files.size(src)) {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
        }
        if (copied > 0) {
            LOG.info("Seeded/synced " + copied + " plugin jar(s) → " + localPlugins);
        }
    }

    /** Count .jar files in an instance plugins dir (0 if missing). */
    public static int countPluginJars(Path rootDir, FleetInstance instance) {
        Path dir = dir(rootDir, instance).resolve("plugins");
        if (!Files.isDirectory(dir) || Files.isSymbolicLink(dir)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return (int) s.filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * If the instance has no jars, seed CORE+NETWORK from catalog. Returns jars copied hint.
     */
    public static int healEmptyPlugins(Path rootDir, ServerConfig config, FleetInstance instance)
            throws IOException {
        Path instDir = dir(rootDir, instance);
        Files.createDirectories(instDir);
        int before = countPluginJars(rootDir, instance);
        if (before > 0) {
            return 0;
        }
        PaperPluginsLayout.ensureUnified(rootDir, instDir);
        seedPluginsFromRoot(rootDir, instDir);
        return countPluginJars(rootDir, instance);
    }

    static void writeServerIdHint(Path dir, String serverId) throws IOException {
        Path hint = dir.resolve("yap-server-id.txt");
        Files.writeString(hint, serverId + "\n");
        Path pd = dir.resolve("plugins/YapPlayerData/config.yml");
        if (!Files.isRegularFile(pd)) {
            pd = dir.resolve("plugins/yap-playerdata/config.yml");
        }
        if (Files.isRegularFile(pd)) {
            String text = Files.readString(pd);
            if (text.contains("server-id:")) {
                text = text.replaceAll("(?m)^(\\s*server-id:\\s*).*$", "$1" + serverId);
            } else {
                text = text + "\nserver-id: " + serverId + "\n";
            }
            Files.writeString(pd, text);
        }
    }
}

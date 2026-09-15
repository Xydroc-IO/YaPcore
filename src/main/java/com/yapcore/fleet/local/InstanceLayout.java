package com.yapcore.fleet.local;

import com.yapcore.config.ServerConfig;
import com.yapcore.folia.FoliaFiles;
import com.yapcore.folia.surface.FoliaSurface;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.paper.PaperOps;
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

    /**
     * Scaffold / start prep for a fleet Folia tree.
     * <p>
     * Never deletes or overwrites operator-installed plugin jars or their config folders.
     * Opening the Control GUI does not call this — only create / Start / enable-fleet do.
     */
    public static void ensure(Path rootDir, ServerConfig config, FleetInstance instance)
            throws IOException {
        Path dir = dir(rootDir, instance);
        Files.createDirectories(dir);
        ensureInstancePluginsDir(dir);
        seedPluginsFromRoot(rootDir, dir);
        Path jar = FoliaFiles.ensureFoliaJar(rootDir, dir, config);
        FoliaFiles.writeEula(dir);
        String bind = instance.bind();
        if ("0.0.0.0".equals(bind)) {
            bind = "";
        }
        Path props = dir.resolve("server.properties");
        if (!Files.isRegularFile(props)) {
            FoliaFiles.writeServerProperties(
                    rootDir,
                    dir,
                    config,
                    instance.port(),
                    bind,
                    "YaP fleet instance " + instance.id() + " server-id=" + instance.serverId());
        } else {
            // Keep operator MOTD / distances / packs — only sync fleet listen identity.
            boolean online = config.isVelocityEnabled() ? false : config.isOnlineMode();
            InstanceServerProps.patch(rootDir, instance, java.util.Map.of(
                    "server-port", Integer.toString(instance.port()),
                    "server-ip", bind == null ? "" : bind,
                    "online-mode", Boolean.toString(online)));
        }
        FoliaFiles.applyVelocitySupport(rootDir, dir, config);
        FoliaSurface.ensureMarker(dir);
        PaperOps.ensure(dir, config);
        writeServerIdHint(dir, instance.serverId());
        LOG.info("Prepared fleet instance " + instance.id() + " jar=" + jar.getFileName()
                + " port=" + instance.port());
    }

    /**
     * Fleet instances keep a private {@code plugins/} tree. Never run the chassis
     * unify/migrate path here — that can wipe jars by linking to root {@code plugins/}.
     */
    static void ensureInstancePluginsDir(Path instanceDir) throws IOException {
        Path local = instanceDir.resolve("plugins");
        if (Files.isSymbolicLink(local)) {
            // Shared link — leave alone; do not convert or empty.
            return;
        }
        Files.createDirectories(local);
    }

    /**
     * Copy missing product default jars from root catalog only.
     * Never overwrites an existing jar (or {@code .jar.disabled}) and never deletes anything.
     */
    public static void seedPluginsFromRoot(Path rootDir, Path instanceDir) throws IOException {
        Path rootPlugins = rootDir.resolve("plugins");
        Path localPlugins = instanceDir.resolve("plugins");
        if (!Files.isDirectory(rootPlugins)) {
            return;
        }
        if (Files.isSymbolicLink(localPlugins)) {
            // Instance shares catalog — nothing to seed into a private tree.
            return;
        }
        Files.createDirectories(localPlugins);
        int copied = 0;
        for (String name : FleetDefaultPlugins.seedJars()) {
            Path src = rootPlugins.resolve(name);
            if (!Files.isRegularFile(src)) {
                continue;
            }
            Path dest = localPlugins.resolve(name);
            Path disabled = localPlugins.resolve(name + ".disabled");
            if (Files.isRegularFile(dest) || Files.isRegularFile(disabled)) {
                continue;
            }
            Files.copy(src, dest, StandardCopyOption.COPY_ATTRIBUTES);
            copied++;
        }
        if (copied > 0) {
            LOG.info("Seeded " + copied + " missing default plugin jar(s) → " + localPlugins);
        }
    }

    /** Count enabled or hard-disabled plugin jars (0 if missing). */
    public static int countPluginJars(Path rootDir, FleetInstance instance) {
        Path dir = dir(rootDir, instance).resolve("plugins");
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return (int) s.filter(p -> Files.isRegularFile(p) && isPluginJarName(p.getFileName().toString()))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }

    static boolean isPluginJarName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".jar") || n.endsWith(".jar.disabled");
    }

    /**
     * If the instance has no jars at all, seed product defaults. Never removes existing jars.
     */
    public static int healEmptyPlugins(Path rootDir, ServerConfig config, FleetInstance instance)
            throws IOException {
        Path instDir = dir(rootDir, instance);
        Files.createDirectories(instDir);
        int before = countPluginJars(rootDir, instance);
        if (before > 0) {
            return 0;
        }
        ensureInstancePluginsDir(instDir);
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

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
import java.util.List;
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
        seedPluginDataFromRoot(rootDir, dir);
        // Catalog is the network source of truth for shared defs (items/kits/QoL/JDBC).
        syncSharedCatalogData(rootDir, dir);
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
            // JE downloads resource-pack-url (GitHub). Keep that URL + SHA-1, not a LAN zip.
            if (InstanceServerProps.syncGithubPackOffer(
                    props,
                    config.getResourcePackUrl(),
                    config.getResourcePackSha1(),
                    config.getResourcePackFile())) {
                LOG.info("Synced GitHub resource pack offer → " + instance.id());
            }
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
            // Heal broken shared links (Paper then fails createDirectories → 0 plugins).
            try {
                Path target = Files.readSymbolicLink(local);
                Path resolved = instanceDir.resolve(target).normalize();
                if (!Files.isDirectory(resolved)) {
                    Files.deleteIfExists(local);
                    Files.createDirectories(local);
                    LOG.warning("Replaced broken plugins symlink in " + instanceDir
                            + " (was → " + target + ")");
                }
            } catch (IOException e) {
                Files.deleteIfExists(local);
                Files.createDirectories(local);
                LOG.warning("Replaced unreadable plugins symlink in " + instanceDir + ": " + e.getMessage());
            }
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

    /**
     * Copy missing plugin data files from the root catalog (e.g. {@code plugins/YaPItems/items/custom})
     * into the instance tree. Never overwrites — first-boot only. Prefer
     * {@link #syncSharedCatalogData} for network-wide definition sync.
     */
    public static void seedPluginDataFromRoot(Path rootDir, Path instanceDir) throws IOException {
        Path rootPlugins = rootDir.resolve("plugins");
        Path localPlugins = instanceDir.resolve("plugins");
        if (!Files.isDirectory(rootPlugins) || Files.isSymbolicLink(localPlugins)) {
            return;
        }
        Files.createDirectories(localPlugins);
        int copied = 0;
        // Product data folders operators edit in the catalog and expect on every backend.
        for (String folder : List.of("YaPItems", "YaPPlayerData", "YaPDB")) {
            Path srcRoot = resolvePluginFolder(rootPlugins, folder);
            if (srcRoot == null) {
                continue;
            }
            Path destRoot = localPlugins.resolve(srcRoot.getFileName().toString());
            copied += copyTreeIfMissing(srcRoot, destRoot);
        }
        if (copied > 0) {
            LOG.info("Seeded " + copied + " missing plugin data file(s) → " + localPlugins);
        }
    }

    /**
     * Push catalog-owned shared definitions into one instance so the fleet behaves like one DB:
     * custom items, kits.yml, QoL knobs, and YaPDB JDBC config stay identical across backends.
     * Never touches {@code YaPPlayerData/config.yml} ({@code server-id} is per-instance).
     *
     * @return number of files written or updated
     */
    public static int syncSharedCatalogData(Path rootDir, Path instanceDir) throws IOException {
        Path rootPlugins = rootDir.resolve("plugins");
        Path localPlugins = instanceDir.resolve("plugins");
        if (!Files.isDirectory(rootPlugins) || Files.isSymbolicLink(localPlugins)) {
            return 0;
        }
        Files.createDirectories(localPlugins);
        int written = 0;
        written += syncRelativeTree(rootPlugins, localPlugins, Path.of("YaPItems", "items"));
        written += syncRelativeFile(rootPlugins, localPlugins, Path.of("YaPItems", "furniture.yml"));
        written += syncRelativeFile(rootPlugins, localPlugins, Path.of("YaPPlayerData", "kits.yml"));
        written += syncRelativeFile(rootPlugins, localPlugins, Path.of("YaPItems", "qol.yml"));
        written += syncRelativeFile(rootPlugins, localPlugins, Path.of("YaPDB", "config.yml"));
        if (written > 0) {
            LOG.info("Synced " + written + " shared catalog file(s) → " + localPlugins);
        }
        return written;
    }

    /**
     * Sync shared catalog definitions into every local {@code fleet/instances/*} tree.
     * Safe to call when fleet is enabled or when the dashboard edits kits/items in the catalog.
     */
    public static int syncSharedCatalogDataToLocalFleet(Path rootDir) throws IOException {
        Path instances = rootDir.resolve("fleet/instances");
        if (!Files.isDirectory(instances)) {
            return 0;
        }
        int total = 0;
        try (Stream<Path> dirs = Files.list(instances)) {
            for (Path inst : dirs.filter(Files::isDirectory).toList()) {
                total += syncSharedCatalogData(rootDir, inst);
            }
        }
        return total;
    }

    /** Copy one catalog-relative file under {@code plugins/} to all local fleet instances. */
    public static int syncCatalogRelativeToLocalFleet(Path rootDir, Path relativeUnderPlugins)
            throws IOException {
        Path rootPlugins = rootDir.resolve("plugins");
        Path src = rootPlugins.resolve(relativeUnderPlugins);
        if (!Files.isRegularFile(src)) {
            return 0;
        }
        Path instances = rootDir.resolve("fleet/instances");
        if (!Files.isDirectory(instances)) {
            return 0;
        }
        int written = 0;
        try (Stream<Path> dirs = Files.list(instances)) {
            for (Path inst : dirs.filter(Files::isDirectory).toList()) {
                Path localPlugins = inst.resolve("plugins");
                if (Files.isSymbolicLink(localPlugins)) {
                    continue;
                }
                written += syncRelativeFile(rootPlugins, localPlugins, relativeUnderPlugins);
            }
        }
        return written;
    }

    private static int syncRelativeTree(Path rootPlugins, Path localPlugins, Path relative)
            throws IOException {
        Path srcRoot = resolveNested(rootPlugins, relative);
        if (srcRoot == null || !Files.isDirectory(srcRoot)) {
            return 0;
        }
        Path destRoot = localPlugins.resolve(relative);
        return copyTreeReplaceChanged(srcRoot, destRoot);
    }

    private static int syncRelativeFile(Path rootPlugins, Path localPlugins, Path relative)
            throws IOException {
        Path src = resolveNestedFile(rootPlugins, relative);
        if (src == null || !Files.isRegularFile(src)) {
            return 0;
        }
        // Keep destination folder casing if it already exists (Folia may rename).
        Path destFolder = resolvePluginFolder(localPlugins, relative.getName(0).toString());
        Path dest = destFolder != null
                ? destFolder.resolve(relative.subpath(1, relative.getNameCount()))
                : localPlugins.resolve(relative);
        return copyFileReplaceChanged(src, dest);
    }

    private static Path resolveNested(Path rootPlugins, Path relative) throws IOException {
        Path direct = rootPlugins.resolve(relative);
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path folder = resolvePluginFolder(rootPlugins, relative.getName(0).toString());
        if (folder == null) {
            return null;
        }
        if (relative.getNameCount() == 1) {
            return folder;
        }
        Path nested = folder.resolve(relative.subpath(1, relative.getNameCount()));
        return Files.isDirectory(nested) ? nested : null;
    }

    private static Path resolveNestedFile(Path rootPlugins, Path relative) throws IOException {
        Path direct = rootPlugins.resolve(relative);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path folder = resolvePluginFolder(rootPlugins, relative.getName(0).toString());
        if (folder == null || relative.getNameCount() < 2) {
            return null;
        }
        Path nested = folder.resolve(relative.subpath(1, relative.getNameCount()));
        return Files.isRegularFile(nested) ? nested : null;
    }

    private static Path resolvePluginFolder(Path pluginsRoot, String folder) throws IOException {
        if (!Files.isDirectory(pluginsRoot)) {
            return null;
        }
        Path direct = pluginsRoot.resolve(folder);
        if (Files.isDirectory(direct)) {
            return direct;
        }
        try (Stream<Path> s = Files.list(pluginsRoot)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(folder))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static int copyTreeIfMissing(Path srcRoot, Path destRoot) throws IOException {
        int copied = 0;
        if (!Files.isDirectory(srcRoot)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(srcRoot)) {
            for (Path src : walk.toList()) {
                if (!Files.isRegularFile(src)) {
                    continue;
                }
                String name = src.getFileName().toString();
                if (name.equals(".gitkeep") || name.endsWith(".disabled")) {
                    continue;
                }
                Path rel = srcRoot.relativize(src);
                Path dest = destRoot.resolve(rel);
                if (Files.isRegularFile(dest)) {
                    continue;
                }
                Files.createDirectories(dest.getParent());
                Files.copy(src, dest, StandardCopyOption.COPY_ATTRIBUTES);
                copied++;
            }
        }
        return copied;
    }

    private static int copyTreeReplaceChanged(Path srcRoot, Path destRoot) throws IOException {
        int written = 0;
        if (!Files.isDirectory(srcRoot)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(srcRoot)) {
            for (Path src : walk.toList()) {
                if (!Files.isRegularFile(src)) {
                    continue;
                }
                String name = src.getFileName().toString();
                if (name.equals(".gitkeep") || name.endsWith(".disabled")) {
                    continue;
                }
                Path rel = srcRoot.relativize(src);
                written += copyFileReplaceChanged(src, destRoot.resolve(rel));
            }
        }
        return written;
    }

    private static int copyFileReplaceChanged(Path src, Path dest) throws IOException {
        if (Files.isRegularFile(dest) && sameFileBytes(src, dest)) {
            return 0;
        }
        Files.createDirectories(dest.getParent());
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        return 1;
    }

    private static boolean sameFileBytes(Path a, Path b) {
        try {
            if (Files.size(a) != Files.size(b)) {
                return false;
            }
            return Files.mismatch(a, b) < 0;
        } catch (IOException e) {
            return false;
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
        seedPluginDataFromRoot(rootDir, instDir);
        syncSharedCatalogData(rootDir, instDir);
        return countPluginJars(rootDir, instance);
    }

    static void writeServerIdHint(Path dir, String serverId) throws IOException {
        Path hint = dir.resolve("yap-server-id.txt");
        Files.writeString(hint, serverId + "\n");
        // Shared-DB plugins must not keep the lobby seed server-id on survival/etc.
        String[] pluginFolders = {
                "YaPPlayerData", "yap-playerdata",
                "YaPClaims", "yap-claims",
                "YaPEssentials", "yap-essentials",
                "YaPPortals", "yap-portals",
                "YaPNpcs", "yap-npcs",
                "YaPRegions", "yap-regions",
                "YaPChat", "yap-chat",
                "YaPProtect", "yap-protect",
                "YaPWorld", "yap-world",
                "YaPTab", "yap-tab"
        };
        for (String folder : pluginFolders) {
            Path cfg = dir.resolve("plugins").resolve(folder).resolve("config.yml");
            patchServerIdInYaml(cfg, serverId);
        }
    }

    static void patchServerIdInYaml(Path cfg, String serverId) throws IOException {
        if (!Files.isRegularFile(cfg)) {
            return;
        }
        String text = Files.readString(cfg);
        if (text.contains("server-id:")) {
            text = text.replaceAll("(?m)^(\\s*server-id:\\s*).*$", "$1" + serverId);
        } else {
            text = text + "\nserver-id: " + serverId + "\n";
        }
        Files.writeString(cfg, text);
    }
}

package com.yapcore.items.item;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Pushes custom-item YAML into the YaP root catalog and every local fleet instance
 * so one {@code /yapitems create} defines the item network-wide.
 * <p>
 * Layout: {@code <root>/plugins/YaPItems/items/custom/} is the source of truth;
 * each {@code fleet/instances/<id>/plugins/YaPItems/} tree mirrors it (same as chassis
 * {@code InstanceLayout.syncSharedCatalogData}).
 */
public final class ItemCatalogPropagator {

    private final Path dataFolder;
    private final Logger log;
    private final boolean enabled;

    public ItemCatalogPropagator(Path dataFolder, Logger log, boolean enabled) {
        this.dataFolder = dataFolder.toAbsolutePath().normalize();
        this.log = log;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /** YaP install root ({@code plugins/} + optional {@code fleet/}), if detectable. */
    public Optional<Path> findYapRoot() {
        return findYapRoot(dataFolder);
    }

    static Optional<Path> findYapRoot(Path dataFolder) {
        Path abs = dataFolder.toAbsolutePath().normalize();
        Path cur = abs;
        while (cur != null) {
            Path name = cur.getFileName();
            Path parent = cur.getParent();
            if (name != null && parent != null && "instances".equals(name.toString())) {
                Path fleetName = parent.getFileName();
                if (fleetName != null && "fleet".equals(fleetName.toString())) {
                    Path root = parent.getParent();
                    if (root != null) {
                        return Optional.of(root);
                    }
                }
            }
            cur = parent;
        }
        Path plugins = abs.getParent();
        if (plugins != null && plugins.getFileName() != null
                && "plugins".equalsIgnoreCase(plugins.getFileName().toString())) {
            Path root = plugins.getParent();
            if (root != null) {
                return Optional.of(root);
            }
        }
        return Optional.empty();
    }

    /**
     * Copy a custom-item YAML into the catalog and all local fleet instance trees.
     *
     * @return number of destinations written (catalog + instances), or 0 if disabled / no root
     */
    public int publishCustomFile(Path localFile) {
        if (!enabled || localFile == null || !Files.isRegularFile(localFile)) {
            return 0;
        }
        Optional<Path> rootOpt = findYapRoot();
        if (rootOpt.isEmpty()) {
            return 0;
        }
        Path root = rootOpt.get();
        Path fileName = localFile.getFileName();
        if (fileName == null) {
            return 0;
        }
        int written = 0;
        try {
            Path catalogDir = root.resolve("plugins").resolve("YaPItems").resolve("items").resolve("custom");
            Files.createDirectories(catalogDir);
            Path catalogFile = catalogDir.resolve(fileName);
            if (!sameFile(localFile, catalogFile)) {
                Files.copy(localFile, catalogFile, StandardCopyOption.REPLACE_EXISTING);
                written++;
            } else if (!Files.isRegularFile(catalogFile)) {
                Files.copy(localFile, catalogFile, StandardCopyOption.REPLACE_EXISTING);
                written++;
            } else {
                // Ensure catalog bytes match (create ran on catalog itself)
                byte[] src = Files.readAllBytes(localFile);
                byte[] dest = Files.readAllBytes(catalogFile);
                if (!java.util.Arrays.equals(src, dest)) {
                    Files.write(catalogFile, src);
                    written++;
                }
            }
            Path instances = root.resolve("fleet").resolve("instances");
            if (!Files.isDirectory(instances)) {
                return written;
            }
            try (Stream<Path> dirs = Files.list(instances)) {
                for (Path inst : dirs.filter(Files::isDirectory).toList()) {
                    Path destPlugins = inst.resolve("plugins");
                    if (Files.isSymbolicLink(destPlugins)) {
                        continue;
                    }
                    Path destDir = destPlugins.resolve("YaPItems").resolve("items").resolve("custom");
                    Files.createDirectories(destDir);
                    Path dest = destDir.resolve(fileName);
                    if (sameFile(localFile, dest) || sameFile(catalogFile, dest)) {
                        continue;
                    }
                    Files.copy(catalogFile, dest, StandardCopyOption.REPLACE_EXISTING);
                    written++;
                }
            }
            if (written > 0) {
                log.info("Synced custom item " + fileName + " to network catalog (" + written + " path(s))");
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to sync custom item to fleet catalog: " + fileName, e);
        }
        return written;
    }

    /** Remove a custom item from catalog + every local fleet instance. */
    public int deleteCustomEverywhere(String id) {
        if (!enabled || id == null || id.isBlank()) {
            return 0;
        }
        Optional<Path> rootOpt = findYapRoot();
        if (rootOpt.isEmpty()) {
            return 0;
        }
        String fileName = id.toLowerCase(Locale.ROOT) + ".yml";
        Path root = rootOpt.get();
        int removed = 0;
        removed += deleteQuiet(root.resolve("plugins").resolve("YaPItems").resolve("items").resolve("custom").resolve(fileName));
        Path instances = root.resolve("fleet").resolve("instances");
        if (!Files.isDirectory(instances)) {
            return removed;
        }
        try (Stream<Path> dirs = Files.list(instances)) {
            for (Path inst : dirs.filter(Files::isDirectory).toList()) {
                Path destPlugins = inst.resolve("plugins");
                if (Files.isSymbolicLink(destPlugins)) {
                    continue;
                }
                removed += deleteQuiet(destPlugins.resolve("YaPItems").resolve("items").resolve("custom").resolve(fileName));
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed listing fleet instances for item delete", e);
        }
        if (removed > 0) {
            log.info("Removed custom item " + fileName + " from network catalog (" + removed + " path(s))");
        }
        return removed;
    }

    private static boolean sameFile(Path a, Path b) {
        try {
            return Files.exists(a) && Files.exists(b) && Files.isSameFile(a, b);
        } catch (IOException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

    private static int deleteQuiet(Path path) {
        try {
            return Files.deleteIfExists(path) ? 1 : 0;
        } catch (IOException e) {
            return 0;
        }
    }
}

package com.yapcore.portals.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mirrors {@code portals.yml} into the YaP catalog under
 * {@code plugins/YaPPortals/servers/<serverId>/portals.yml} so a fleet instance
 * reseed does not wipe hub pads.
 */
public final class PortalCatalogMirror {

    private final Path dataFolder;
    private final String serverId;
    private final Logger log;

    public PortalCatalogMirror(Path dataFolder, String serverId, Logger log) {
        this.dataFolder = dataFolder.toAbsolutePath().normalize();
        this.serverId = serverId == null || serverId.isBlank() ? "lobby" : serverId.trim();
        this.log = log;
    }

    public Optional<Path> findYapRoot() {
        Path abs = dataFolder;
        Path cur = abs;
        while (cur != null) {
            Path name = cur.getFileName();
            Path parent = cur.getParent();
            if (name != null && parent != null && "instances".equals(name.toString())) {
                Path fleet = parent.getFileName();
                if (fleet != null && "fleet".equals(fleet.toString())) {
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

    /** Copy instance portals.yml → catalog servers/&lt;id&gt;/portals.yml. */
    public void publish(Path localPortalsFile) {
        if (localPortalsFile == null || !Files.isRegularFile(localPortalsFile)) {
            return;
        }
        Optional<Path> root = findYapRoot();
        if (root.isEmpty()) {
            return;
        }
        try {
            Path destDir = root.get().resolve("plugins").resolve("YaPPortals")
                    .resolve("servers").resolve(serverId);
            Files.createDirectories(destDir);
            Path dest = destDir.resolve("portals.yml");
            Files.copy(localPortalsFile, dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Mirrored portals.yml → catalog servers/" + serverId);
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to mirror portals.yml to catalog", e);
        }
    }

    /**
     * If the instance portals file is missing or only has an empty map, restore from catalog.
     *
     * @return true if a catalog file was copied into place
     */
    public boolean restoreIfEmpty(Path localPortalsFile) {
        Optional<Path> root = findYapRoot();
        if (root.isEmpty()) {
            return false;
        }
        Path catalog = root.get().resolve("plugins").resolve("YaPPortals")
                .resolve("servers").resolve(serverId).resolve("portals.yml");
        if (!Files.isRegularFile(catalog)) {
            return false;
        }
        try {
            String body = Files.isRegularFile(localPortalsFile)
                    ? Files.readString(localPortalsFile) : "";
            boolean empty = !Files.isRegularFile(localPortalsFile)
                    || body.contains("portals: {}")
                    || !body.contains("target-server");
            if (!empty) {
                return false;
            }
            Files.createDirectories(localPortalsFile.getParent());
            Files.copy(catalog, localPortalsFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Restored portals.yml from catalog servers/" + serverId);
            return true;
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to restore portals.yml from catalog", e);
            return false;
        }
    }
}

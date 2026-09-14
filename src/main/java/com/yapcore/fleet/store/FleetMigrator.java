package com.yapcore.fleet.store;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.link.LinkFleetSync;
import com.yapcore.fleet.local.InstanceLayout;
import com.yapcore.fleet.model.FleetInstance;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Idempotent migration: {@code folia-kernel} → {@code fleet/instances/lobby} and rewrite chassis + Link.
 */
public final class FleetMigrator {

    private static final Logger LOG = Logger.getLogger("YaP.Fleet.Migrator");

    private final Path rootDir;
    private final ServerConfig config;
    private final FleetStore store;

    public FleetMigrator(Path rootDir, ServerConfig config, FleetStore store) {
        this.rootDir = rootDir.toAbsolutePath().normalize();
        this.config = config;
        this.store = store;
    }

    /** @return true if migration ran or was already complete */
    public synchronized boolean enableFleet() throws IOException {
        if (store.exists()) {
            store.load();
            if (!store.findInstance(store.primaryId()).isPresent()
                    && store.instances().isEmpty()) {
                seedLobbyOnly();
            }
            ensureAllLocalLayouts();
            applyChassisPaths();
            LinkFleetSync.syncAll(rootDir, config, store);
            config.setFleetEnabled(true);
            config.save();
            LOG.info("Fleet already registered at " + store.fleetFile());
            return true;
        }

        Path legacy = rootDir.resolve(config.getFoliaDir()).toAbsolutePath().normalize();
        Path lobbyDir = store.instancesRoot().resolve("lobby").toAbsolutePath().normalize();
        Files.createDirectories(store.instancesRoot());

        if (Files.exists(lobbyDir) && !sameTreeOrEmpty(legacy, lobbyDir)) {
            if (isUsableInstanceDir(lobbyDir) && !isUsableInstanceDir(legacy)) {
                // Lobby already prepared; just register.
            } else if (isUsableInstanceDir(legacy) && !Files.isSameFile(legacy, lobbyDir)) {
                throw new FileAlreadyExistsException(
                        "Cannot migrate: " + lobbyDir + " already exists with different content");
            }
        }

        if (isUsableInstanceDir(legacy) && !Files.exists(lobbyDir)) {
            moveOrCopy(legacy, lobbyDir);
            LOG.info("Migrated " + legacy.getFileName() + " → " + lobbyDir);
        } else if (!Files.exists(lobbyDir)) {
            Files.createDirectories(lobbyDir);
            LOG.info("Created empty lobby instance dir " + lobbyDir);
        }

        int port = config.foliaListenPort();
        store.setPrimaryId("lobby");
        store.putInstance(FleetInstance.lobby(port));
        ensureAllLocalLayouts();
        applyChassisPaths();
        LinkFleetSync.syncAll(rootDir, config, store);
        config.setFleetEnabled(true);
        config.save();
        return true;
    }

    private void seedLobbyOnly() throws IOException {
        int port = config.foliaListenPort();
        Path lobbyDir = store.instancesRoot().resolve("lobby");
        Files.createDirectories(lobbyDir);
        store.setPrimaryId("lobby");
        store.putInstance(FleetInstance.lobby(port));
        ensureAllLocalLayouts();
    }

    private void ensureAllLocalLayouts() throws IOException {
        for (FleetInstance inst : store.instances()) {
            if (inst.isLocal()) {
                InstanceLayout.ensure(rootDir, config, inst);
            }
        }
    }

    private void applyChassisPaths() throws IOException {
        String rel = "fleet/instances/" + store.primaryId();
        config.setFoliaDir(rel);
        config.setFleetEnabled(true);
        config.save();
    }

    static boolean isUsableInstanceDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    static boolean sameTreeOrEmpty(Path legacy, Path lobby) throws IOException {
        if (!Files.exists(legacy)) {
            return true;
        }
        if (!Files.exists(lobby)) {
            return true;
        }
        return Files.isSameFile(legacy, lobby);
    }

    static void moveOrCopy(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        try {
            Files.move(from, to);
            return;
        } catch (IOException moveFailed) {
            LOG.warning("Move failed (" + moveFailed.getMessage() + "); copying " + from + " → " + to);
        }
        copyRecursive(from, to);
        deleteRecursive(from);
    }

    static void copyRecursive(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path src : walk.toList()) {
                Path rel = from.relativize(src);
                Path dst = to.resolve(rel.toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst);
                } else {
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup after copy
                }
            });
        }
    }
}

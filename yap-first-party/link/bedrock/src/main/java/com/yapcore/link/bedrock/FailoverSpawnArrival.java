package com.yapcore.link.bedrock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Marks a YaPPortals pending SPAWN arrival so mid-session backend failover
 * lands at the destination's {@code /setspawn} instead of last-logout
 * (often the portal pad).
 * <p>
 * File format matches {@code PortalArrivalPending}:
 * {@code target\nspawn\ntimestamp} under
 * {@code plugins/YaPPortals/pending-spawn/<uuid>}.
 */
public final class FailoverSpawnArrival {

    private static final Logger LOG = Logger.getLogger("YaP.Link.FailoverSpawn");

    private FailoverSpawnArrival() {
    }

    /**
     * Write a SPAWN pending arrival for {@code uuid} → {@code targetServer}.
     * Best-effort: missing YaP root only logs; SoftSwitch still proceeds.
     */
    public static void mark(Path linkHome, UUID uuid, String targetServer) {
        if (uuid == null || targetServer == null || targetServer.isBlank()) {
            return;
        }
        Optional<Path> root = findYapRoot(linkHome);
        if (root.isEmpty()) {
            LOG.warning("FAILOVER spawn mark skipped for " + uuid
                    + " — cannot resolve YaP root from link home " + linkHome);
            return;
        }
        Path dir = root.get().resolve("plugins").resolve("YaPPortals").resolve("pending-spawn");
        writeSpawnFile(dir, uuid, targetServer.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Like {@link #mark} but does not overwrite an existing pending file.
     * Portal Connect already wrote island/rtp/home — SoftSwitch must not clobber it.
     * {@code /hub} / {@code /server} SoftSwitch with no prior mark gets SPAWN.
     */
    public static void markIfAbsent(Path linkHome, UUID uuid, String targetServer) {
        if (uuid == null || targetServer == null || targetServer.isBlank()) {
            return;
        }
        Optional<Path> root = findYapRoot(linkHome);
        if (root.isEmpty()) {
            LOG.warning("SOFT-SWITCH spawn mark skipped for " + uuid
                    + " — cannot resolve YaP root from link home " + linkHome);
            return;
        }
        Path dir = root.get().resolve("plugins").resolve("YaPPortals").resolve("pending-spawn");
        Path file = dir.resolve(uuid.toString());
        if (Files.isRegularFile(file)) {
            LOG.fine("SOFT-SWITCH spawn mark skipped — pending already exists for " + uuid);
            return;
        }
        writeSpawnFile(dir, uuid, targetServer.trim().toLowerCase(Locale.ROOT));
    }

    private static void writeSpawnFile(Path dir, UUID uuid, String target) {
        String body = target + "\nspawn\n" + System.currentTimeMillis();
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(uuid.toString());
            Files.writeString(file, body, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            LOG.info("SPAWN arrival marked " + uuid + " → " + target + " at " + file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "SPAWN arrival mark failed for " + uuid + " → " + target, e);
        }
    }

    /** Resolve YaP chassis root from Link's data home ({@code …/link-data}). */
    static Optional<Path> findYapRoot(Path linkHome) {
        if (linkHome == null) {
            return Optional.empty();
        }
        Path abs = linkHome.toAbsolutePath().normalize();
        Path parent = abs.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("plugins"))) {
            return Optional.of(parent);
        }
        Path cur = abs;
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("plugins").resolve("YaPPortals"))
                    || Files.isDirectory(cur.resolve("fleet").resolve("instances"))) {
                return Optional.of(cur);
            }
            cur = cur.getParent();
        }
        return Optional.empty();
    }
}

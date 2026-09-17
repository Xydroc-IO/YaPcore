package com.yapcore.portals.store;

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
 * Cross-backend pending portal arrivals under
 * {@code plugins/YaPPortals/pending-spawn/<uuid>}.
 * Written on the source before Connect; consumed on the destination join.
 */
public final class PortalArrivalPending {

    private static final long TTL_MS = 120_000L;

    private final PortalCatalogMirror mirror;
    private final Logger log;

    public PortalArrivalPending(PortalCatalogMirror mirror, Logger log) {
        this.mirror = mirror;
        this.log = log;
    }

    public void mark(UUID uuid, String targetServer) {
        if (uuid == null || targetServer == null || targetServer.isBlank()) {
            return;
        }
        Optional<Path> dir = pendingDir();
        if (dir.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(dir.get());
            String body = targetServer.trim().toLowerCase(Locale.ROOT) + "\n" + System.currentTimeMillis();
            Files.writeString(dir.get().resolve(uuid.toString()), body, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to mark portal arrival for " + uuid, e);
        }
    }

    /**
     * If this player has a pending arrival for {@code thisServerId}, consume and return true.
     */
    public boolean consume(UUID uuid, String thisServerId) {
        if (uuid == null || thisServerId == null) {
            return false;
        }
        Optional<Path> dir = pendingDir();
        if (dir.isEmpty()) {
            return false;
        }
        Path file = dir.get().resolve(uuid.toString());
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            String body = Files.readString(file, StandardCharsets.UTF_8).trim();
            Files.deleteIfExists(file);
            String[] parts = body.split("\\R", 2);
            String target = parts[0].trim().toLowerCase(Locale.ROOT);
            long markedAt = 0L;
            if (parts.length > 1) {
                try {
                    markedAt = Long.parseLong(parts[1].trim());
                } catch (NumberFormatException ignored) {
                    // treat as expired if unreadable
                }
            }
            if (markedAt > 0L && System.currentTimeMillis() - markedAt > TTL_MS) {
                return false;
            }
            return thisServerId.trim().equalsIgnoreCase(target);
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to consume portal arrival for " + uuid, e);
            return false;
        }
    }

    private Optional<Path> pendingDir() {
        return mirror.findYapRoot()
                .map(root -> root.resolve("plugins").resolve("YaPPortals").resolve("pending-spawn"));
    }
}

package com.yapcore.portals.store;

import com.yapcore.portals.PortalArrival;

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
 * <p>
 * File body variants:
 * <ul>
 *   <li>{@code target\narrival\ntimestamp} — spawn / rtp</li>
 *   <li>{@code target\nhome\nhomeName\ntimestamp} — home arrival</li>
 *   <li>Older {@code target\ntimestamp} — spawn</li>
 * </ul>
 */
public final class PortalArrivalPending {

    private static final long TTL_MS = 120_000L;

    public record ArrivalRequest(PortalArrival arrival, String homeName) {
        public ArrivalRequest {
            arrival = arrival == null ? PortalArrival.SPAWN : arrival;
            homeName = homeName == null || homeName.isBlank() ? "home" : homeName.trim().toLowerCase(Locale.ROOT);
        }
    }

    private final PortalCatalogMirror mirror;
    private final Logger log;

    public PortalArrivalPending(PortalCatalogMirror mirror, Logger log) {
        this.mirror = mirror;
        this.log = log;
    }

    public void mark(UUID uuid, String targetServer) {
        mark(uuid, targetServer, PortalArrival.SPAWN, "home");
    }

    public void mark(UUID uuid, String targetServer, PortalArrival arrival) {
        mark(uuid, targetServer, arrival, "home");
    }

    public void mark(UUID uuid, String targetServer, PortalArrival arrival, String homeName) {
        if (uuid == null || targetServer == null || targetServer.isBlank()) {
            return;
        }
        PortalArrival mode = arrival == null ? PortalArrival.SPAWN : arrival;
        Optional<Path> dir = pendingDir();
        if (dir.isEmpty()) {
            log.warning("Portal arrival mark skipped for " + uuid
                    + " — cannot resolve YaP root (pending-spawn)");
            return;
        }
        try {
            Files.createDirectories(dir.get());
            String body;
            if (mode == PortalArrival.HOME) {
                String home = homeName == null || homeName.isBlank() ? "home" : homeName.trim().toLowerCase(Locale.ROOT);
                body = targetServer.trim().toLowerCase(Locale.ROOT)
                        + "\nhome\n" + home
                        + "\n" + System.currentTimeMillis();
            } else {
                body = targetServer.trim().toLowerCase(Locale.ROOT)
                        + "\n" + mode.name().toLowerCase(Locale.ROOT)
                        + "\n" + System.currentTimeMillis();
            }
            Path file = dir.get().resolve(uuid.toString());
            Files.writeString(file, body, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            log.info("Portal arrival marked " + uuid + " → " + targetServer.trim().toLowerCase(Locale.ROOT)
                    + " (" + mode.name().toLowerCase(Locale.ROOT) + ") at " + file);
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to mark portal arrival for " + uuid, e);
        }
    }

    /**
     * If this player has a pending arrival for {@code thisServerId}, consume and return it.
     * Leaves the file in place when the target does not match this backend (another JVM may own it).
     */
    public Optional<ArrivalRequest> consume(UUID uuid, String thisServerId) {
        if (uuid == null || thisServerId == null) {
            return Optional.empty();
        }
        Optional<Path> dir = pendingDir();
        if (dir.isEmpty()) {
            log.warning("Portal arrival consume skipped for " + uuid
                    + " — cannot resolve YaP root (pending-spawn)");
            return Optional.empty();
        }
        Path file = dir.get().resolve(uuid.toString());
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String body = Files.readString(file, StandardCharsets.UTF_8).trim();
            String[] parts = body.split("\\R");
            if (parts.length == 0 || parts[0].isBlank()) {
                Files.deleteIfExists(file);
                return Optional.empty();
            }
            String target = parts[0].trim().toLowerCase(Locale.ROOT);
            PortalArrival arrival = PortalArrival.SPAWN;
            String homeName = "home";
            long markedAt = 0L;
            if (parts.length == 2) {
                try {
                    markedAt = Long.parseLong(parts[1].trim());
                } catch (NumberFormatException ignored) {
                    // treat as expired if unreadable
                }
            } else if (parts.length == 3) {
                arrival = PortalArrival.parse(parts[1]);
                try {
                    markedAt = Long.parseLong(parts[2].trim());
                } catch (NumberFormatException ignored) {
                    // treat as expired if unreadable
                }
            } else if (parts.length >= 4) {
                arrival = PortalArrival.parse(parts[1]);
                homeName = parts[2].trim().toLowerCase(Locale.ROOT);
                if (homeName.isBlank()) {
                    homeName = "home";
                }
                try {
                    markedAt = Long.parseLong(parts[3].trim());
                } catch (NumberFormatException ignored) {
                    // treat as expired if unreadable
                }
            }
            if (markedAt > 0L && System.currentTimeMillis() - markedAt > TTL_MS) {
                Files.deleteIfExists(file);
                log.info("Portal arrival expired for " + uuid + " (target was " + target + ")");
                return Optional.empty();
            }
            if (!thisServerId.trim().equalsIgnoreCase(target)) {
                // Wrong backend read the shared pending file — leave it for the real destination.
                return Optional.empty();
            }
            Files.deleteIfExists(file);
            return Optional.of(new ArrivalRequest(arrival, homeName));
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to consume portal arrival for " + uuid, e);
            return Optional.empty();
        }
    }

    private Optional<Path> pendingDir() {
        return mirror.findYapRoot()
                .map(root -> root.resolve("plugins").resolve("YaPPortals").resolve("pending-spawn"));
    }
}

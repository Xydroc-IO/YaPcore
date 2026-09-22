package com.yapcore.link.backend;

import com.yapcore.link.LinkConfig;
import com.yapcore.link.status.ServerStatus;
import com.yapcore.link.status.StatusPing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Probes backends and caches status for ping passthrough + try failover + fleet UI. */
public final class BackendMonitor {

    private static final Logger LOG = Logger.getLogger("YaP.Link.BackendMonitor");

    /**
     * @param latencyMs probe round-trip; {@code -1} when down / never measured
     */
    public record Snapshot(
            boolean up,
            ServerStatus status,
            long checkedAtMs,
            String error,
            long latencyMs) {
        static Snapshot down(String error) {
            return new Snapshot(false, null, System.currentTimeMillis(), error, -1L);
        }
    }

    private final AtomicReference<LinkConfig> configRef;
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public BackendMonitor(LinkConfig config) {
        this.configRef = new AtomicReference<>(config);
    }

    public void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "yap-link-backend-probe");
            t.setDaemon(true);
            return t;
        });
        probeAll();
        LinkConfig cfg = configRef.get();
        int sec = Math.max(3, cfg.backendProbeIntervalSec());
        scheduler.scheduleAtFixedRate(this::probeAll, sec, sec, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public void updateConfig(LinkConfig config) {
        configRef.set(config);
        probeAll();
    }

    public LinkConfig config() {
        return configRef.get();
    }

    public Snapshot snapshot(String serverName) {
        return snapshots.getOrDefault(serverName, Snapshot.down("never probed"));
    }

    public boolean isUp(String serverName) {
        Snapshot s = snapshots.get(serverName);
        return s != null && s.up();
    }

    public void probeAll() {
        LinkConfig cfg = configRef.get();
        for (LinkConfig.Backend b : cfg.servers().values()) {
            probeOne(b, cfg.backendProbeTimeoutMs());
        }
    }

    private void probeOne(LinkConfig.Backend backend, int timeoutMs) {
        long t0 = System.nanoTime();
        try {
            ServerStatus status = StatusPing.pingBlocking(backend, timeoutMs);
            long latencyMs = Math.max(0L, (System.nanoTime() - t0) / 1_000_000L);
            snapshots.put(backend.name(),
                    new Snapshot(true, status, System.currentTimeMillis(), null, latencyMs));
            LOG.fine("probe OK " + backend.name() + " online=" + status.online()
                    + " latencyMs=" + latencyMs);
        } catch (Exception e) {
            snapshots.put(backend.name(), Snapshot.down(e.getMessage()));
            LOG.log(Level.FINE, "probe DOWN " + backend.name() + ": " + e.getMessage());
        }
    }

    /**
     * Pick login backend. Preferred name (soft-switch redirect / forced-host) wins when UP.
     * Otherwise first UP from {@code try}. When {@link LinkConfig#forceDefaultServer()} is true,
     * never fall through to an arbitrary UP backend outside {@code try} (keeps hub-first joins).
     */
    public LinkConfig.Backend pickLoginTarget(String preferredName) {
        LinkConfig cfg = configRef.get();
        if (preferredName != null) {
            LinkConfig.Backend forced = cfg.findServer(preferredName);
            if (forced != null && isUp(forced.name())) {
                return forced;
            }
            if (forced != null && !cfg.skipDownOnForcedHost()) {
                return forced;
            }
        }
        for (String name : cfg.tryOrder()) {
            if (isUp(name)) {
                LinkConfig.Backend b = cfg.findServer(name);
                if (b != null) {
                    return b;
                }
            }
        }
        if (!cfg.forceDefaultServer()) {
            for (LinkConfig.Backend b : cfg.servers().values()) {
                if (isUp(b.name())) {
                    return b;
                }
            }
        }
        return cfg.resolveTry();
    }

    /**
     * Pick hub for mid-session failover when {@code excludeName} (current backend) died.
     * Prefers configured {@link LinkConfig#fallbackServer()}, then first other {@code try} entry.
     * Returns a candidate even if the probe briefly says DOWN — soft-switch retries TCP.
     */
    public LinkConfig.Backend pickFallback(String excludeName) {
        LinkConfig cfg = configRef.get();
        String preferred = cfg.fallbackServer();
        LinkConfig.Backend preferredBackend = preferred == null || preferred.isBlank()
                ? null
                : cfg.findServer(preferred);
        if (preferredBackend != null
                && (excludeName == null || !preferredBackend.name().equalsIgnoreCase(excludeName))) {
            if (isUp(preferredBackend.name()) || cfg.findServer(preferredBackend.name()) != null) {
                return preferredBackend;
            }
        }
        for (String name : cfg.tryOrder()) {
            if (excludeName != null && name.equalsIgnoreCase(excludeName)) {
                continue;
            }
            if (isUp(name)) {
                LinkConfig.Backend b = cfg.findServer(name);
                if (b != null) {
                    return b;
                }
            }
        }
        for (String name : cfg.tryOrder()) {
            if (excludeName != null && name.equalsIgnoreCase(excludeName)) {
                continue;
            }
            LinkConfig.Backend b = cfg.findServer(name);
            if (b != null) {
                return b;
            }
        }
        return null;
    }

    /** Aggregate status for proxy ping (sum online, sum max, merge samples). */
    public ServerStatus aggregateStatus() {
        LinkConfig cfg = configRef.get();
        int online = 0;
        int summedMax = 0;
        String motd = cfg.motd();
        int protocol = 776;
        String versionName = "YaP Link";
        List<ServerStatus> up = new ArrayList<>();
        for (LinkConfig.Backend b : cfg.servers().values()) {
            Snapshot snap = snapshots.get(b.name());
            if (snap != null && snap.up() && snap.status() != null) {
                up.add(snap.status());
                if (cfg.aggregatePlayerCount()) {
                    online += snap.status().online();
                    // Only UP backends count — shut down survival must drop MOTD max (250 not 500).
                    summedMax += Math.max(0, snap.status().max());
                }
            }
        }
        // Live capacity = sum of up backends. link max-players is a ceiling, never a floor.
        int max = applyMaxCeiling(summedMax, cfg.maxPlayers());
        if (up.isEmpty()) {
            return ServerStatus.synthetic(motd, 0, max, protocol, versionName);
        }
        ServerStatus primary = up.getFirst();
        if (!cfg.aggregatePlayerCount()) {
            return primary;
        }
        return ServerStatus.synthetic(
                motd.isBlank() ? primary.descriptionText() : motd,
                online,
                max,
                primary.protocol() > 0 ? primary.protocol() : protocol,
                primary.versionName()
        );
    }

    /** Advertised max: live sum, optionally capped by configured network headroom. */
    public static int applyMaxCeiling(int liveSum, int configuredCap) {
        int max = Math.max(0, liveSum);
        if (configuredCap > 0 && max > configuredCap) {
            return configuredCap;
        }
        return max;
    }

    public Map<String, Snapshot> allSnapshots() {
        return Map.copyOf(snapshots);
    }
}

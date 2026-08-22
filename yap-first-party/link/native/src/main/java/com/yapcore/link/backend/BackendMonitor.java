package com.yapcore.link.backend;

import com.yapcore.link.LinkConfig;
import com.yapcore.link.status.ServerStatus;
import com.yapcore.link.status.StatusPing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Probes backends and caches status for ping passthrough + try failover. */
public final class BackendMonitor {

    private static final Logger LOG = Logger.getLogger("YaP.Link.BackendMonitor");

    public record Snapshot(boolean up, ServerStatus status, long checkedAtMs, String error) {
        static Snapshot down(String error) {
            return new Snapshot(false, null, System.currentTimeMillis(), error);
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
        try {
            ServerStatus status = StatusPing.pingBlocking(backend, timeoutMs);
            snapshots.put(backend.name(), new Snapshot(true, status, System.currentTimeMillis(), null));
            LOG.fine("probe OK " + backend.name() + " online=" + status.online());
        } catch (Exception e) {
            snapshots.put(backend.name(), Snapshot.down(e.getMessage()));
            LOG.log(Level.FINE, "probe DOWN " + backend.name() + ": " + e.getMessage());
        }
    }

    /** Pick first UP server from try order, else first UP any, else resolveTry fallback. */
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
        for (LinkConfig.Backend b : cfg.servers().values()) {
            if (isUp(b.name())) {
                return b;
            }
        }
        return cfg.resolveTry();
    }

    /** Aggregate status for proxy ping (sum online, max max, merge samples). */
    public ServerStatus aggregateStatus() {
        LinkConfig cfg = configRef.get();
        int online = 0;
        int max = cfg.maxPlayers();
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
                    max = Math.max(max, snap.status().max());
                }
            }
        }
        if (up.isEmpty()) {
            return ServerStatus.synthetic(motd, 0, max, protocol, versionName);
        }
        ServerStatus primary = up.getFirst();
        if (!cfg.aggregatePlayerCount()) {
            return primary;
        }
        if (cfg.pingPassthrough() && up.size() == 1) {
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

    public Map<String, Snapshot> allSnapshots() {
        return Map.copyOf(snapshots);
    }
}

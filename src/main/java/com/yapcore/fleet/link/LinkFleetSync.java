package com.yapcore.fleet.link;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.local.InstanceServerProps;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.fleet.store.FleetStore;
import com.yapcore.web.DashboardLinkSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

/** Rewrites Link {@code servers.*} / try / bedrock-backend from the fleet registry. */
public final class LinkFleetSync {

    private static final Logger LOG = Logger.getLogger("YaP.Fleet.LinkSync");

    private LinkFleetSync() {
    }

    public static void syncAll(Path rootDir, ServerConfig config, FleetStore store) throws IOException {
        Path home = DashboardLinkSnapshot.resolveHome(rootDir, config.getLinkEmbedHome());
        Files.createDirectories(home);
        Path propsFile = home.resolve("link.properties");
        Properties p = new Properties();
        if (Files.isRegularFile(propsFile)) {
            try (InputStream in = Files.newInputStream(propsFile)) {
                p.load(in);
            }
        }
        // Drop previous servers.*
        List<String> remove = new ArrayList<>();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("servers.") || key.equals("servers")) {
                remove.add(key);
            }
        }
        remove.forEach(p::remove);

        List<String> tryOrder = new ArrayList<>();
        FleetInstance primary = store.findInstance(store.primaryId()).orElse(null);
        for (FleetInstance inst : store.instances()) {
            if (!inst.isLocal() && !inst.nodeId().equalsIgnoreCase("local")) {
                // Remote instances still need a reachable address — use bind:port as configured.
            }
            String key = "servers." + inst.serverId();
            p.setProperty(key, inst.address());
            tryOrder.add(inst.serverId());
        }
        if (primary != null) {
            // Primary first in try list
            tryOrder.remove(primary.serverId());
            tryOrder.add(0, primary.serverId());
            p.setProperty("bedrock-backend", primary.address());
        }
        if (!tryOrder.isEmpty()) {
            p.setProperty("try", String.join(",", tryOrder));
        }
        // Network headroom ceiling in link.properties = sum of configured Folia max-players.
        // Live MOTD max still comes from BackendMonitor (UP backends only).
        int networkMax = 0;
        for (FleetInstance inst : store.instances()) {
            try {
                Map<String, String> props = InstanceServerProps.read(rootDir, inst);
                String raw = props.get("max-players");
                if (raw != null && !raw.isBlank()) {
                    networkMax += Math.max(0, Integer.parseInt(raw.trim()));
                }
            } catch (Exception ignored) {
                // Keep prior max-players if an instance props file is missing.
            }
        }
        if (networkMax > 0) {
            p.setProperty("max-players", Integer.toString(networkMax));
        }
        p.setProperty("aggregate-player-count", "true");
        p.setProperty("skip-down-on-forced-host", "true");
        try (OutputStream out = Files.newOutputStream(propsFile)) {
            p.store(out, "YaP Link — synced from fleet registry");
        }
        LOG.info("Synced Link servers.* for " + store.instances().size() + " instance(s)");
    }

    /** Parse servers.* map from properties (for tests / tooling). */
    public static Map<String, String> readServers(Properties p) {
        Map<String, String> out = new LinkedHashMap<>();
        if (p == null) {
            return out;
        }
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("servers.") && key.length() > "servers.".length()) {
                out.put(key.substring("servers.".length()), p.getProperty(key));
            }
        }
        return out;
    }

    public static List<String> readTryOrder(Properties p) {
        if (p == null) {
            return List.of();
        }
        String raw = p.getProperty("try", "");
        if (raw.isBlank()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String part : raw.split("[,\\s]+")) {
            if (!part.isBlank()) {
                seen.add(part.trim());
            }
        }
        return new ArrayList<>(seen);
    }
}

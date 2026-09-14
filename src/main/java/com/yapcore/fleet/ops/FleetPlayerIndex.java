package com.yapcore.fleet.ops;

import com.yapcore.fleet.link.BackendHealthBridge;
import com.yapcore.fleet.model.BackendHealth;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.fleet.model.FleetNode;
import com.yapcore.fleet.remote.FleetAgentClient;
import com.yapcore.fleet.store.FleetStore;
import com.yapcore.config.ServerConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Aggregates player samples from Link backend health + remote agents. */
public final class FleetPlayerIndex {

    private static final Logger LOG = Logger.getLogger("YaP.Fleet.Players");

    private FleetPlayerIndex() {
    }

    public static List<Map<String, Object>> collect(
            PathRoot paths,
            ServerConfig config,
            FleetStore store,
            FleetAgentClient agents,
            boolean linkRunning) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<BackendHealth> health = BackendHealthBridge.collect(
                paths.rootDir(), config.getLinkEmbedHome(), linkRunning);
        for (BackendHealth h : health) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("serverId", h.name());
            row.put("online", h.online());
            row.put("max", h.max());
            row.put("up", h.up());
            row.put("latencyMs", h.latencyMs());
            row.put("source", "link-backend");
            row.put("nodeId", resolveNode(store, h.name()));
            out.add(row);
        }
        for (FleetNode node : store.nodes()) {
            try {
                for (Map<String, Object> p : agents.players(node)) {
                    Map<String, Object> row = new LinkedHashMap<>(p);
                    row.putIfAbsent("nodeId", node.id());
                    row.putIfAbsent("source", "agent");
                    out.add(row);
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "agent players " + node.id() + ": " + e.getMessage());
            }
        }
        return out;
    }

    private static String resolveNode(FleetStore store, String serverId) {
        for (FleetInstance i : store.instances()) {
            if (i.serverId().equalsIgnoreCase(serverId) || i.id().equalsIgnoreCase(serverId)) {
                return i.nodeId();
            }
        }
        return "local";
    }

    /** Tiny indirection to keep method signature clear for callers. */
    public record PathRoot(java.nio.file.Path rootDir) {
    }
}

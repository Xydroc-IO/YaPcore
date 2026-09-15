package com.yapcore.fleet.service;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.local.InstanceLayout;
import com.yapcore.fleet.local.LocalInstanceSupervisor;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.fleet.store.FleetStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared-catalog heal/sync/reload helpers for {@link FleetService}. */
final class FleetCatalogOps {

    private static final Logger LOG = Logger.getLogger("YaP.Fleet");

    private FleetCatalogOps() {
    }

    static void healEmptyPluginsQuiet(Path rootDir, ServerConfig config, FleetStore store, boolean enabled) {
        if (!enabled && !store.exists()) {
            return;
        }
        for (FleetInstance inst : store.instances()) {
            if (!inst.isLocal()) {
                continue;
            }
            try {
                if (InstanceLayout.countPluginJars(rootDir, inst) == 0) {
                    int n = InstanceLayout.healEmptyPlugins(rootDir, config, inst);
                    if (n > 0) {
                        LOG.info("Healed empty plugins on " + inst.id() + " → " + n + " jar(s)");
                    }
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Heal plugins " + inst.id(), e);
            }
        }
    }

    /** Push catalog kits/items/QoL/JDBC into every local fleet backend (one-network content). */
    static void syncSharedCatalogQuiet(Path rootDir) {
        try {
            int n = InstanceLayout.syncSharedCatalogDataToLocalFleet(rootDir);
            if (n > 0) {
                LOG.info("Synced " + n + " shared catalog file(s) across local fleet instances");
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Shared catalog sync", e);
        }
    }

    /**
     * Catalog → all local instances for shared definitions (items, kits, QoL, YaPDB JDBC).
     * Call after editing catalog YAML or when aligning a fleet that drifted.
     */
    static Map<String, Object> syncSharedCatalog(Path rootDir, FleetStore store) throws IOException {
        int files = InstanceLayout.syncSharedCatalogDataToLocalFleet(rootDir);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("action", "sync-shared-catalog");
        out.put("filesWritten", files);
        out.put("instances", store.instances().stream().filter(FleetInstance::isLocal).map(FleetInstance::id).toList());
        return out;
    }

    /** Reload shared data plugins on every running local instance (kits / items / playerdata). */
    static Map<String, Object> reloadSharedCatalogOnRunning(
            FleetStore store, ConcurrentHashMap<String, LocalInstanceSupervisor> local) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (FleetInstance inst : store.instances()) {
            if (!inst.isLocal()) {
                continue;
            }
            LocalInstanceSupervisor sup = local.get(inst.id());
            if (sup == null || !sup.isRunning()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("instanceId", inst.id());
            try {
                row.put("yapdata", sup.dispatch("yapdata reload"));
                row.put("yapitems", safeDispatch(sup, "yapitems reload"));
                row.put("ok", true);
            } catch (Exception e) {
                row.put("ok", false);
                row.put("error", e.getMessage());
            }
            results.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", results.stream().allMatch(r -> Boolean.TRUE.equals(r.get("ok"))) || results.isEmpty());
        out.put("results", results);
        return out;
    }

    private static String safeDispatch(LocalInstanceSupervisor sup, String line) {
        try {
            return sup.dispatch(line);
        } catch (Exception e) {
            return e.getMessage() == null ? "failed" : e.getMessage();
        }
    }
}

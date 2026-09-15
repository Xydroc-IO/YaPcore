package com.yapcore.fleet.service;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.link.BackendHealthBridge;
import com.yapcore.fleet.local.InstanceLayout;
import com.yapcore.fleet.local.LocalInstanceSupervisor;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.fleet.model.FleetNode;
import com.yapcore.fleet.model.InstanceState;
import com.yapcore.fleet.ops.FleetPlayerIndex;
import com.yapcore.fleet.remote.FleetAgentClient;
import com.yapcore.fleet.store.FleetStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Status / instance snapshot builders for {@link FleetService}. */
final class FleetStatusSnapshot {

    private FleetStatusSnapshot() {
    }

    static Map<String, Object> status(
            Path rootDir,
            ServerConfig config,
            FleetStore store,
            FleetAgentClient agents,
            ConcurrentHashMap<String, LocalInstanceSupervisor> local,
            boolean fleetEnabled,
            BooleanSupplier linkRunning) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("fleetEnabled", fleetEnabled);
        out.put("fleetFilePresent", store.exists());
        out.put("primaryId", store.primaryId());
        out.put("instances", snapshotInstances(rootDir, store, local));
        out.put("nodes", store.nodes().stream().map(FleetNode::toMap).toList());
        out.put("backendHealth", BackendHealthBridge.collectMaps(
                rootDir, config.getLinkEmbedHome(), linkRunning.getAsBoolean()));
        out.put("players", FleetPlayerIndex.collect(
                new FleetPlayerIndex.PathRoot(rootDir),
                config, store, agents, linkRunning.getAsBoolean()));
        return out;
    }

    static List<Map<String, Object>> snapshotInstances(
            Path rootDir,
            FleetStore store,
            ConcurrentHashMap<String, LocalInstanceSupervisor> local) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (FleetInstance inst : store.instances()) {
            Map<String, Object> m = new LinkedHashMap<>(inst.toMap());
            if (inst.isLocal()) {
                LocalInstanceSupervisor sup = local.get(inst.id());
                InstanceState st = sup == null ? InstanceState.STOPPED : sup.state();
                m.put("state", st.name());
                m.put("running", st == InstanceState.RUNNING);
                m.put("lastError", sup == null ? null : sup.lastError());
                m.put("pluginCount", InstanceLayout.countPluginJars(rootDir, inst));
            } else {
                m.put("state", "REMOTE");
                m.put("running", false);
                m.put("pluginCount", null);
            }
            list.add(m);
        }
        return list;
    }
}

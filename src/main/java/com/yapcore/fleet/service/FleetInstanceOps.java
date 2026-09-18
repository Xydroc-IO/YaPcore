package com.yapcore.fleet.service;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.link.LinkFleetSync;
import com.yapcore.fleet.local.FleetInstancePlugins;
import com.yapcore.fleet.local.InstanceLayout;
import com.yapcore.fleet.local.InstancePlayerDataSettings;
import com.yapcore.fleet.local.InstanceServerProps;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.fleet.ops.FleetDeploy;
import com.yapcore.fleet.remote.FleetAgentClient;
import com.yapcore.fleet.store.FleetStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Instance settings + plugin catalog install helpers for {@link FleetService}. */
public final class FleetInstanceOps {

    private FleetInstanceOps() {
    }

    public static Map<String, Object> readSettings(Path rootDir, FleetStore store, String id)
            throws IOException {
        FleetInstance inst = require(store, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("instance", inst.toMap());
        out.put("properties", InstanceServerProps.read(rootDir, inst));
        Map<String, Object> heap = new LinkedHashMap<>();
        heap.put("ramMb", inst.ramMb());
        heap.put("ramMinMb", inst.ramMinMb());
        heap.put("inherit", inst.ramMb() <= 0);
        out.put("heap", heap);
        out.put("playerData", InstancePlayerDataSettings.read(rootDir, inst));
        return out;
    }

    public static Map<String, Object> writeSettings(
            Path rootDir,
            ServerConfig config,
            FleetStore store,
            String id,
            Map<String, String> body) throws IOException {
        return writeSettings(rootDir, config, store, id, body, null);
    }

    public static Map<String, Object> writeSettings(
            Path rootDir,
            ServerConfig config,
            FleetStore store,
            String id,
            Map<String, String> body,
            RestartFn reloadFn) throws IOException {
        FleetInstance inst = require(store, id);
        if (!inst.isLocal()) {
            throw new IOException("Remote instance settings via agent not implemented yet: " + id);
        }
        Map<String, String> propUpdates = new LinkedHashMap<>();
        copyIfPresent(body, propUpdates, "motd");
        copyIfPresent(body, propUpdates, "max-players");
        copyIfPresent(body, propUpdates, "gamemode");
        copyIfPresent(body, propUpdates, "difficulty");
        copyIfPresent(body, propUpdates, "view-distance");
        copyIfPresent(body, propUpdates, "simulation-distance");
        copyIfPresent(body, propUpdates, "online-mode");
        copyIfPresent(body, propUpdates, "pvp");
        copyIfPresent(body, propUpdates, "spawn-protection");
        copyIfPresent(body, propUpdates, "white-list");
        copyIfPresent(body, propUpdates, "enforce-whitelist");
        copyIfPresent(body, propUpdates, "enable-command-block");
        copyIfPresent(body, propUpdates, "force-gamemode");
        copyIfPresent(body, propUpdates, "spawn-monsters");
        copyIfPresent(body, propUpdates, "spawn-animals");
        copyIfPresent(body, propUpdates, "level-name");
        copyIfPresent(body, propUpdates, "level-seed");
        copyIfPresent(body, propUpdates, "level-type");
        copyIfPresent(body, propUpdates, "generator-settings");

        Integer newPort = parseInt(body.get("port"));
        if (newPort == null) {
            newPort = parseInt(body.get("server-port"));
        }
        String newBind = blankToNull(body.get("bind"));
        if (newBind == null) {
            newBind = blankToNull(body.get("server-ip"));
        }
        String newServerId = blankToNull(body.get("serverId"));
        String newDisplay = blankToNull(body.get("displayName"));
        Boolean autoStart = parseBool(body.get("autoStart"));
        Integer ramMb = parseInt(firstPresent(body, "ramMb", "ram-mb"));
        Integer ramMinMb = parseInt(firstPresent(body, "ramMinMb", "ram-min-mb"));

        FleetInstance updated = inst;
        boolean registryChanged = false;
        if (newPort != null && newPort > 0 && newPort != inst.port()) {
            if (store.portUsedByOther(newPort, inst.id())) {
                throw new IOException("Port " + newPort + " is already used by another fleet server");
            }
            propUpdates.put("server-port", Integer.toString(newPort));
            updated = updated.withPort(newPort);
            registryChanged = true;
        }
        if (newBind != null && !newBind.equals(inst.bind())) {
            propUpdates.put("server-ip", "0.0.0.0".equals(newBind) ? "" : newBind);
            updated = updated.withBind(newBind);
            registryChanged = true;
        }
        if (newServerId != null && !newServerId.equals(inst.serverId())) {
            updated = updated.withServerId(newServerId);
            registryChanged = true;
        }
        if (newDisplay != null && !newDisplay.equals(inst.displayName())) {
            updated = updated.withDisplayName(newDisplay);
            registryChanged = true;
        }
        if (autoStart != null && autoStart != inst.autoStart()) {
            updated = updated.withAutoStart(autoStart);
            registryChanged = true;
        }
        if (ramMb != null || ramMinMb != null) {
            int nextRam = ramMb != null ? ramMb : inst.ramMb();
            int nextMin = ramMinMb != null ? ramMinMb : inst.ramMinMb();
            if (nextRam != inst.ramMb() || nextMin != inst.ramMinMb()) {
                updated = updated.withRam(nextRam, nextMin);
                registryChanged = true;
            }
        }

        // Persist registry BEFORE Link sync so backends see the new port.
        if (registryChanged) {
            store.putInstance(updated);
        }
        // Patch props for this instance only — never rewrite sibling instance trees.
        InstanceLayout.ensure(rootDir, config, updated);
        Map<String, String> props = InstanceServerProps.patch(rootDir, updated, propUpdates);
        boolean playerDataChanged = InstancePlayerDataSettings.write(rootDir, updated, body);
        LinkFleetSync.syncAll(rootDir, config, store);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("action", "update-settings");
        out.put("instance", updated.toMap());
        out.put("properties", props);
        out.put("playerData", InstancePlayerDataSettings.read(rootDir, updated));
        out.put("playerDataChanged", playerDataChanged);
        out.put("linkSynced", true);
        if (playerDataChanged && reloadFn != null) {
            try {
                reloadFn.restart(updated.id());
                out.put("playerDataReloaded", true);
            } catch (Exception e) {
                out.put("playerDataReloaded", false);
                out.put("playerDataReloadNote",
                        "Saved on disk — restart this server or run yapdata reload to apply.");
            }
        } else if (playerDataChanged) {
            out.put("playerDataReloadNote",
                    "Saved on disk — restart this server or run yapdata reload to apply.");
        }
        if (registryChanged && (ramMb != null || ramMinMb != null)) {
            out.put("note", "RAM changes apply on next Start/Restart of this server.");
        }
        return out;
    }

    public static Map<String, Object> listPlugins(Path rootDir, FleetStore store, String id)
            throws IOException {
        FleetInstance inst = require(store, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("instanceId", inst.id());
        out.put("plugins", FleetInstancePlugins.list(rootDir, inst));
        return out;
    }

    public static Map<String, Object> setPluginEnabled(
            Path rootDir, FleetStore store, String id, String jar, boolean enable)
            throws IOException {
        FleetInstance inst = require(store, id);
        Map<String, Object> result = FleetInstancePlugins.setHardEnabled(rootDir, inst, jar, enable);
        result.put("instanceId", inst.id());
        return result;
    }

    public static Map<String, Object> uninstallPlugin(
            Path rootDir, FleetStore store, String id, String jar) throws IOException {
        return FleetInstancePlugins.uninstall(rootDir, require(store, id), jar);
    }

    public static Map<String, Object> installCatalogJar(
            Path rootDir,
            FleetStore store,
            FleetAgentClient agents,
            List<String> instanceIds,
            String jarName,
            String restartPolicy,
            RestartFn restartFn) throws Exception {
        if (instanceIds == null || instanceIds.isEmpty()) {
            throw new IOException("instanceIds required");
        }
        String name = jarName == null ? "" : jarName.trim();
        if (name.isEmpty()) {
            throw new IOException("jar required");
        }
        Map<String, Object> deploy = FleetDeploy.deployFromRootPlugins(
                rootDir, store, agents, instanceIds, name, restartPolicy);
        applyRestartPolicy(deploy, restartPolicy, instanceIds, restartFn);
        Map<String, Object> out = new LinkedHashMap<>(deploy);
        out.put("action", "install-plugin");
        out.put("jar", name);
        return out;
    }

    public static List<String> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static void applyRestartPolicy(
            Map<String, Object> deployResult,
            String restartPolicy,
            List<String> instanceIds,
            RestartFn restartFn) throws Exception {
        String policy = restartPolicy == null ? "none" : restartPolicy.trim().toLowerCase();
        if ("none".equals(policy) || policy.isEmpty() || restartFn == null) {
            return;
        }
        List<Map<String, Object>> restarts = new ArrayList<>();
        for (String id : instanceIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("instanceId", id);
            try {
                if ("restart".equals(policy) || "if-running".equals(policy)) {
                    restartFn.restart(id);
                    row.put("ok", true);
                    row.put("restarted", true);
                } else {
                    row.put("ok", true);
                    row.put("restarted", false);
                    row.put("note", "unknown policy " + policy);
                }
            } catch (Exception e) {
                row.put("ok", false);
                row.put("error", e.getMessage());
            }
            restarts.add(row);
        }
        deployResult.put("restarts", restarts);
    }

    private static FleetInstance require(FleetStore store, String id) throws IOException {
        return store.findInstance(id).orElseThrow(() -> new IOException("Unknown instance: " + id));
    }

    private static void copyIfPresent(Map<String, String> src, Map<String, String> dest, String key) {
        String v = src.get(key);
        if (v != null && !v.isBlank()) {
            dest.put(key, v.trim());
        }
    }

    private static String firstPresent(Map<String, String> src, String... keys) {
        for (String key : keys) {
            String v = src.get(key);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean parseBool(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(raw.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw.trim())) {
            return false;
        }
        return null;
    }

    @FunctionalInterface
    public interface RestartFn {
        void restart(String instanceId) throws Exception;
    }
}

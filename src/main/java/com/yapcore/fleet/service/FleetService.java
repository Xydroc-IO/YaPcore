package com.yapcore.fleet.service;

import com.yapcore.config.ServerConfig;
import com.yapcore.fleet.link.LinkFleetSync;
import com.yapcore.fleet.local.InstanceLayout;
import com.yapcore.fleet.local.LocalInstanceSupervisor;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.fleet.model.FleetNode;
import com.yapcore.fleet.ops.DatabaseSetup;
import com.yapcore.fleet.ops.FleetDeploy;
import com.yapcore.fleet.ops.NetworkBootstrap;
import com.yapcore.fleet.remote.FleetAgentClient;
import com.yapcore.fleet.store.FleetMigrator;
import com.yapcore.fleet.store.FleetStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Facade used by dashboard + Swing GUI for fleet operations. */
public final class FleetService {

    private static final Logger LOG = Logger.getLogger("YaP.Fleet");

    private final Path rootDir;
    private final ServerConfig config;
    private final FleetStore store;
    private final FleetMigrator migrator;
    private final FleetAgentClient agents;
    private final ConcurrentHashMap<String, LocalInstanceSupervisor> local = new ConcurrentHashMap<>();
    private BooleanSupplier linkRunning = () -> false;

    public FleetService(Path rootDir, ServerConfig config) {
        this.rootDir = rootDir.toAbsolutePath().normalize();
        this.config = config;
        this.store = new FleetStore(this.rootDir);
        this.migrator = new FleetMigrator(this.rootDir, config, store);
        this.agents = new FleetAgentClient(this.rootDir);
        if (store.exists()) {
            try {
                store.load();
                FleetCatalogOps.healEmptyPluginsQuiet(rootDir, config, store, isEnabled());
                FleetCatalogOps.syncSharedCatalogQuiet(rootDir);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to load fleet.json", e);
            }
        }
    }

    public void setLinkRunningSupplier(BooleanSupplier supplier) {
        this.linkRunning = supplier == null ? () -> false : supplier;
    }

    public FleetStore store() {
        return store;
    }

    public boolean isEnabled() {
        return config.isFleetEnabled();
    }

    public synchronized Map<String, Object> enableFleet() throws IOException {
        migrator.enableFleet();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("action", "enable-fleet");
        out.put("fleetFile", store.fleetFile().toString());
        out.put("primaryId", store.primaryId());
        out.put("instances", FleetStatusSnapshot.snapshotInstances(rootDir, store, local));
        return out;
    }

    public synchronized Map<String, Object> createInstance(
            String id, String displayName, Integer port, boolean autoStart) throws IOException {
        return createInstance(id, displayName, port, autoStart, null, null);
    }

    public synchronized Map<String, Object> createInstance(
            String id,
            String displayName,
            Integer port,
            boolean autoStart,
            Integer ramMb,
            Integer ramMinMb) throws IOException {
        requireEnabled();
        if (store.findInstance(id).isPresent()) {
            throw new IOException("Instance already exists: " + id);
        }
        int p;
        if (port != null && port > 0) {
            if (store.portUsed(port)) {
                throw new IOException("Port " + port + " is already used by another fleet server");
            }
            p = port;
        } else {
            // Folia game ports start at folia-port (default 25567). Never auto-pick Link/Via :25565/:25566.
            p = store.nextFreeGamePort(config);
        }
        int ram = ramMb == null || ramMb <= 0
                ? Math.max(1024, config.getRamMb() / 2)
                : ramMb;
        int ramMin = ramMinMb == null || ramMinMb <= 0 ? Math.min(512, ram) : Math.min(ramMinMb, ram);
        FleetInstance inst = new FleetInstance(
                id, id, "local", "fleet/instances/" + id.trim().toLowerCase(),
                p, "127.0.0.1", autoStart,
                displayName == null || displayName.isBlank() ? id : displayName,
                ram, ramMin);
        InstanceLayout.ensure(rootDir, config, inst);
        store.putInstance(inst);
        LinkFleetSync.syncAll(rootDir, config, store);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("action", "create");
        out.put("instance", inst.toMap());
        return out;
    }

    public synchronized Map<String, Object> deleteInstance(String id) throws IOException {
        requireEnabled();
        LocalInstanceSupervisor sup = local.get(id);
        if (sup != null && sup.isRunning()) {
            throw new IOException("Stop instance before delete: " + id);
        }
        if (id.equalsIgnoreCase(store.primaryId())) {
            throw new IOException("Cannot delete primary instance: " + id);
        }
        boolean removed = store.removeInstance(id);
        local.remove(id);
        if (removed) {
            LinkFleetSync.syncAll(rootDir, config, store);
        }
        return Map.of("ok", removed, "action", "delete", "instanceId", id);
    }

    public synchronized void startInstance(String id) throws Exception {
        requireEnabled();
        FleetInstance inst = store.findInstance(id)
                .orElseThrow(() -> new IOException("Unknown instance: " + id));
        if (!inst.isLocal()) {
            FleetNode node = store.findNode(inst.nodeId())
                    .orElseThrow(() -> new IOException("Unknown node: " + inst.nodeId()));
            agents.startInstance(node, inst.id());
            return;
        }
        supervisor(inst).start();
    }

    public synchronized void stopInstance(String id) throws Exception {
        requireEnabled();
        FleetInstance inst = store.findInstance(id)
                .orElseThrow(() -> new IOException("Unknown instance: " + id));
        if (!inst.isLocal()) {
            FleetNode node = store.findNode(inst.nodeId())
                    .orElseThrow(() -> new IOException("Unknown node: " + inst.nodeId()));
            agents.stopInstance(node, inst.id());
            return;
        }
        LocalInstanceSupervisor sup = local.get(id);
        if (sup != null) {
            sup.stop();
        }
    }

    public synchronized void restartInstance(String id) throws Exception {
        requireEnabled();
        FleetInstance inst = store.findInstance(id)
                .orElseThrow(() -> new IOException("Unknown instance: " + id));
        if (!inst.isLocal()) {
            FleetNode node = store.findNode(inst.nodeId())
                    .orElseThrow(() -> new IOException("Unknown node: " + inst.nodeId()));
            agents.restartInstance(node, inst.id());
            return;
        }
        stopInstance(id);
        Thread.sleep(750);
        startInstance(id);
    }

    public Map<String, Object> readInstanceSettings(String id) throws IOException {
        requireEnabled();
        return FleetInstanceOps.readSettings(rootDir, store, id);
    }

    public Map<String, Object> writeInstanceSettings(String id, Map<String, String> body)
            throws IOException {
        requireEnabled();
        return FleetInstanceOps.writeSettings(rootDir, config, store, id, body);
    }

    public Map<String, Object> listInstancePlugins(String id) throws IOException {
        requireEnabled();
        FleetInstance inst = store.findInstance(id)
                .orElseThrow(() -> new IOException("Unknown instance: " + id));
        if (inst.isLocal() && InstanceLayout.countPluginJars(rootDir, inst) == 0) {
            InstanceLayout.healEmptyPlugins(rootDir, config, inst);
        }
        return FleetInstanceOps.listPlugins(rootDir, store, id);
    }

    /** Install missing CORE+NETWORK jars onto an instance from the root catalog. */
    public Map<String, Object> installCoreNetworkDefaults(String id) throws IOException {
        requireEnabled();
        FleetInstance inst = store.findInstance(id)
                .orElseThrow(() -> new IOException("Unknown instance: " + id));
        if (!inst.isLocal()) {
            throw new IOException("Remote CORE+NETWORK install via agent not implemented yet: " + id);
        }
        InstanceLayout.ensure(rootDir, config, inst);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("action", "install-core-network");
        out.put("instanceId", id);
        out.put("pluginCount", InstanceLayout.countPluginJars(rootDir, inst));
        return out;
    }

    /**
     * Catalog → all local instances for shared definitions (items, kits, QoL, YaPDB JDBC).
     * Call after editing catalog YAML or when aligning a fleet that drifted.
     */
    public Map<String, Object> syncSharedCatalog() throws IOException {
        requireEnabled();
        return FleetCatalogOps.syncSharedCatalog(rootDir, store);
    }

    /** Reload shared data plugins on every running local instance (kits / items / playerdata). */
    public Map<String, Object> reloadSharedCatalogOnRunning() {
        return FleetCatalogOps.reloadSharedCatalogOnRunning(store, local);
    }

    public Map<String, Object> setInstancePluginEnabled(String id, String jar, boolean enable)
            throws IOException {
        requireEnabled();
        return FleetInstanceOps.setPluginEnabled(rootDir, store, id, jar, enable);
    }

    public Map<String, Object> uninstallInstancePlugin(String id, String jar) throws IOException {
        requireEnabled();
        return FleetInstanceOps.uninstallPlugin(rootDir, store, id, jar);
    }

    public Map<String, Object> installCatalogJar(
            String jarName, List<String> instanceIds, String restartPolicy) throws Exception {
        requireEnabled();
        return FleetInstanceOps.installCatalogJar(
                rootDir, store, agents, instanceIds, jarName, restartPolicy, this::restartInstance);
    }

    public synchronized void startAutoInstances() throws Exception {
        if (!isEnabled()) {
            return;
        }
        if (!store.exists()) {
            store.load();
        }
        for (FleetInstance inst : store.instances()) {
            if (inst.autoStart() && inst.isLocal()) {
                startInstance(inst.id());
            }
        }
    }

    public synchronized void stopAllLocal() {
        for (LocalInstanceSupervisor sup : local.values()) {
            try {
                sup.stop();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "stop " + (sup.instance() != null ? sup.instance().id() : "?"), e);
            }
        }
    }

    public String dispatch(String instanceId, String line) throws Exception {
        requireEnabled();
        FleetInstance inst = store.findInstance(instanceId)
                .orElseThrow(() -> new IOException("Unknown instance: " + instanceId));
        if (!inst.isLocal()) {
            FleetNode node = store.findNode(inst.nodeId())
                    .orElseThrow(() -> new IOException("Unknown node: " + inst.nodeId()));
            Map<String, Object> resp = agents.command(node, inst.id(), line);
            return String.valueOf(resp.getOrDefault("result", resp));
        }
        return supervisor(inst).dispatch(line);
    }

    public Optional<LocalInstanceSupervisor> localSupervisor(String id) {
        return Optional.ofNullable(local.get(id));
    }

    public Map<String, Object> statusSnapshot() {
        return FleetStatusSnapshot.status(
                rootDir, config, store, agents, local, isEnabled(), linkRunning);
    }

    public Map<String, Object> setAutoStart(String id, boolean autoStart) throws IOException {
        requireEnabled();
        FleetInstance inst = store.findInstance(id)
                .orElseThrow(() -> new IOException("Unknown instance: " + id));
        FleetInstance updated = inst.withAutoStart(autoStart);
        store.putInstance(updated);
        return Map.of("ok", true, "action", "set-auto-start", "instance", updated.toMap());
    }

    public Map<String, Object> syncLink() throws IOException {
        requireEnabled();
        LinkFleetSync.syncAll(rootDir, config, store);
        return Map.of("ok", true, "action", "sync-link", "instances", store.instances().size());
    }

    public Map<String, Object> putNode(FleetNode node) throws IOException {
        requireEnabled();
        Path tokenPath = rootDir.resolve(node.tokenRef());
        Files.createDirectories(tokenPath.getParent());
        if (!Files.isRegularFile(tokenPath)) {
            throw new IOException("Create token file first: " + tokenPath);
        }
        store.putNode(node);
        return Map.of("ok", true, "action", "put-node", "node", node.toMap());
    }

    public Map<String, Object> removeNode(String id) throws IOException {
        requireEnabled();
        boolean removed = store.removeNode(id);
        return Map.of("ok", removed, "action", "remove-node", "nodeId", id);
    }

    public Map<String, Object> probeNode(String id) throws IOException {
        requireEnabled();
        FleetNode node = store.findNode(id).orElseThrow(() -> new IOException("Unknown node: " + id));
        return agents.health(node);
    }

    public Map<String, Object> deploy(
            List<String> instanceIds, Path source, String target, String restartPolicy)
            throws Exception {
        requireEnabled();
        Map<String, Object> out = FleetDeploy.deployFile(
                rootDir, store, agents, instanceIds, source, target, restartPolicy);
        FleetInstanceOps.applyRestartPolicy(out, restartPolicy, instanceIds, this::restartInstance);
        return out;
    }

    public Map<String, Object> bootstrap(
            String jdbcUrl, boolean createSurvival, boolean enableVelocity, boolean startInstances)
            throws Exception {
        return NetworkBootstrap.run(
                rootDir, config, store, migrator, jdbcUrl, createSurvival, enableVelocity,
                startInstances, this::startInstance);
    }

    /** Standalone DB wizard: MariaDB / Postgres / SQLite (+ Docker when needed) → catalog sync. */
    public Map<String, Object> ensureDatabase(
            String engine, String serverId, String host, boolean syncFleet) throws Exception {
        return DatabaseSetup.ensure(
                rootDir,
                DatabaseSetup.Engine.parse(engine),
                serverId,
                host,
                syncFleet);
    }

    public Map<String, Object> databaseStatus() {
        return DatabaseSetup.status(rootDir);
    }

    public Map<String, Object> startDatabaseDocker(String engine) throws Exception {
        return DatabaseSetup.startDocker(rootDir, DatabaseSetup.Engine.parse(engine));
    }

    public Map<String, Object> stopDatabaseDocker(String engine) throws Exception {
        return DatabaseSetup.stopDocker(rootDir, DatabaseSetup.Engine.parse(engine));
    }

    public boolean isPrimaryRunning() {
        if (!isEnabled()) {
            return false;
        }
        LocalInstanceSupervisor sup = local.get(store.primaryId());
        return sup != null && sup.isRunning();
    }

    public String dispatchPrimary(String line) {
        LocalInstanceSupervisor sup = local.get(store.primaryId());
        if (sup == null) {
            return "Primary fleet instance not running";
        }
        return sup.dispatch(line);
    }

    private LocalInstanceSupervisor supervisor(FleetInstance inst) {
        LocalInstanceSupervisor existing = local.get(inst.id());
        if (existing != null && existing.isRunning()) {
            return existing;
        }
        LocalInstanceSupervisor created = new LocalInstanceSupervisor(rootDir, config, inst);
        local.put(inst.id(), created);
        return created;
    }

    private void requireEnabled() throws IOException {
        if (!isEnabled() && !store.exists()) {
            throw new IOException("Fleet not enabled — POST action=enable-fleet first");
        }
        if (!isEnabled()) {
            config.setFleetEnabled(true);
            config.save();
        }
        if (store.exists()) {
            try {
                store.load();
            } catch (IOException ignored) {
                // keep memory
            }
        }
    }
}

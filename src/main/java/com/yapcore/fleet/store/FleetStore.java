package com.yapcore.fleet.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.yapcore.fleet.model.FleetInstance;
import com.yapcore.fleet.model.FleetNode;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Persists {@code fleet/fleet.json} registry of instances and remote nodes. */
public final class FleetStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final Path rootDir;
    private final Path fleetFile;
    private final CopyOnWriteArrayList<FleetInstance> instances = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<FleetNode> nodes = new CopyOnWriteArrayList<>();
    private String primaryId = "lobby";
    private int schemaVersion = 1;

    public FleetStore(Path rootDir) {
        this.rootDir = rootDir.toAbsolutePath().normalize();
        this.fleetFile = this.rootDir.resolve("fleet/fleet.json");
    }

    public Path rootDir() {
        return rootDir;
    }

    public Path fleetFile() {
        return fleetFile;
    }

    public Path instancesRoot() {
        return rootDir.resolve("fleet/instances");
    }

    public boolean exists() {
        return Files.isRegularFile(fleetFile);
    }

    public synchronized void load() throws IOException {
        instances.clear();
        nodes.clear();
        if (!Files.isRegularFile(fleetFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(fleetFile, StandardCharsets.UTF_8)) {
            Map<String, Object> root = GSON.fromJson(reader, MAP_TYPE);
            if (root == null) {
                return;
            }
            schemaVersion = asInt(root.get("schemaVersion"), 1);
            primaryId = ObjectsToString(root.get("primaryId"), "lobby");
            Object inst = root.get("instances");
            if (inst instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        FleetInstance fi = FleetInstance.fromMap((Map<String, Object>) m);
                        if (fi != null) {
                            instances.add(fi);
                        }
                    }
                }
            }
            Object nd = root.get("nodes");
            if (nd instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        FleetNode node = FleetNode.fromMap((Map<String, Object>) m);
                        if (node != null) {
                            nodes.add(node);
                        }
                    }
                }
            }
        }
    }

    public synchronized void save() throws IOException {
        Files.createDirectories(fleetFile.getParent());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", schemaVersion);
        root.put("primaryId", primaryId);
        List<Map<String, Object>> instMaps = new ArrayList<>();
        for (FleetInstance i : instances) {
            instMaps.add(i.toMap());
        }
        root.put("instances", instMaps);
        List<Map<String, Object>> nodeMaps = new ArrayList<>();
        for (FleetNode n : nodes) {
            nodeMaps.add(n.toMap());
        }
        root.put("nodes", nodeMaps);
        try (Writer writer = Files.newBufferedWriter(fleetFile, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    public List<FleetInstance> instances() {
        return Collections.unmodifiableList(instances);
    }

    public List<FleetNode> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    public String primaryId() {
        return primaryId;
    }

    public void setPrimaryId(String id) {
        this.primaryId = id == null || id.isBlank() ? "lobby" : id.trim();
    }

    public Optional<FleetInstance> findInstance(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim();
        return instances.stream().filter(i -> i.id().equalsIgnoreCase(key)).findFirst();
    }

    public Optional<FleetNode> findNode(String id) {
        if (id == null || "local".equalsIgnoreCase(id)) {
            return Optional.empty();
        }
        String key = id.trim();
        return nodes.stream().filter(n -> n.id().equalsIgnoreCase(key)).findFirst();
    }

    public synchronized void putInstance(FleetInstance instance) throws IOException {
        instances.removeIf(i -> i.id().equalsIgnoreCase(instance.id()));
        instances.add(instance);
        save();
    }

    public synchronized boolean removeInstance(String id) throws IOException {
        boolean removed = instances.removeIf(i -> i.id().equalsIgnoreCase(id));
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized void putNode(FleetNode node) throws IOException {
        nodes.removeIf(n -> n.id().equalsIgnoreCase(node.id()));
        nodes.add(node);
        save();
    }

    public synchronized boolean removeNode(String id) throws IOException {
        boolean removed = nodes.removeIf(n -> n.id().equalsIgnoreCase(id));
        if (removed) {
            save();
        }
        return removed;
    }

    public int nextFreePort(int preferred) {
        int port = preferred <= 0 ? 25567 : preferred;
        while (portUsed(port)) {
            port++;
        }
        return port;
    }

    /**
     * Next free Folia listen port for a new game server. Skips chassis/Link reserved ports
     * so lobby/survival/etc. never all land on 25566.
     */
    public int nextFreeGamePort(com.yapcore.config.ServerConfig config) {
        int start = config.getFoliaPort();
        if (start <= 0) {
            start = 25567;
        }
        // Prefer band at/after folia-port; skip anything already registered.
        int port = Math.max(start, config.foliaListenPort());
        int guard = 0;
        while (portUsed(port) || isReservedPort(config, port)) {
            port++;
            if (++guard > 10_000) {
                throw new IllegalStateException("No free fleet game port found");
            }
        }
        return port;
    }

    public boolean portUsed(int port) {
        for (FleetInstance i : instances) {
            if (i.port() == port) {
                return true;
            }
        }
        return false;
    }

    public boolean portUsedByOther(int port, String exceptId) {
        String skip = exceptId == null ? "" : exceptId.trim();
        for (FleetInstance i : instances) {
            if (i.port() == port && !i.id().equalsIgnoreCase(skip)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReservedPort(com.yapcore.config.ServerConfig config, int port) {
        if (port == config.getPort()) {
            return true; // chassis / Via JE
        }
        if (port == config.getBedrockPort()) {
            return true;
        }
        if (port == config.getWebDashboardPort()) {
            return true;
        }
        // Conventional Link player port when chassis JE is 25566
        if (port == 25565) {
            return true;
        }
        return false;
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    private static String ObjectsToString(Object o, String def) {
        if (o == null) {
            return def;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? def : s;
    }
}

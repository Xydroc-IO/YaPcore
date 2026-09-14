package com.yapcore.fleet.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Registered game JVM in the fleet (local or remote node). */
public final class FleetInstance {

    /** 0 = inherit chassis default at start time. */
    public static final int RAM_INHERIT = 0;

    private final String id;
    private final String serverId;
    private final String nodeId;
    private final String relativeDir;
    private final int port;
    private final String bind;
    private final boolean autoStart;
    private final String displayName;
    private final int ramMb;
    private final int ramMinMb;

    public FleetInstance(
            String id,
            String serverId,
            String nodeId,
            String relativeDir,
            int port,
            String bind,
            boolean autoStart,
            String displayName) {
        this(id, serverId, nodeId, relativeDir, port, bind, autoStart, displayName,
                RAM_INHERIT, RAM_INHERIT);
    }

    public FleetInstance(
            String id,
            String serverId,
            String nodeId,
            String relativeDir,
            int port,
            String bind,
            boolean autoStart,
            String displayName,
            int ramMb,
            int ramMinMb) {
        this.id = requireId(id);
        this.serverId = serverId == null || serverId.isBlank() ? this.id : serverId.trim();
        this.nodeId = nodeId == null || nodeId.isBlank() ? "local" : nodeId.trim();
        this.relativeDir = relativeDir == null || relativeDir.isBlank()
                ? "fleet/instances/" + this.id
                : relativeDir.trim();
        this.port = port;
        this.bind = bind == null || bind.isBlank() ? "127.0.0.1" : bind.trim();
        this.autoStart = autoStart;
        this.displayName = displayName == null || displayName.isBlank() ? this.id : displayName.trim();
        this.ramMb = clampRam(ramMb);
        this.ramMinMb = clampRam(ramMinMb);
    }

    public static FleetInstance lobby(int port) {
        return new FleetInstance(
                "lobby", "lobby", "local", "fleet/instances/lobby", port, "127.0.0.1", true, "Lobby");
    }

    public String id() {
        return id;
    }

    public String serverId() {
        return serverId;
    }

    public String nodeId() {
        return nodeId;
    }

    public boolean isLocal() {
        return "local".equalsIgnoreCase(nodeId);
    }

    public String relativeDir() {
        return relativeDir;
    }

    public int port() {
        return port;
    }

    public String bind() {
        return bind;
    }

    public boolean autoStart() {
        return autoStart;
    }

    public String displayName() {
        return displayName;
    }

    /** Max heap MB for this JVM; {@link #RAM_INHERIT} means use chassis default. */
    public int ramMb() {
        return ramMb;
    }

    /** Min heap MB; {@link #RAM_INHERIT} means auto (≤512 or half of max). */
    public int ramMinMb() {
        return ramMinMb;
    }

    public String address() {
        return bind + ":" + port;
    }

    public FleetInstance withAutoStart(boolean value) {
        return copy(port, bind, value, displayName, serverId, ramMb, ramMinMb);
    }

    public FleetInstance withPort(int newPort) {
        return copy(newPort, bind, autoStart, displayName, serverId, ramMb, ramMinMb);
    }

    public FleetInstance withBind(String newBind) {
        return copy(port, newBind, autoStart, displayName, serverId, ramMb, ramMinMb);
    }

    public FleetInstance withServerId(String newServerId) {
        return copy(port, bind, autoStart, displayName, newServerId, ramMb, ramMinMb);
    }

    public FleetInstance withDisplayName(String newDisplayName) {
        return copy(port, bind, autoStart, newDisplayName, serverId, ramMb, ramMinMb);
    }

    public FleetInstance withRam(int newRamMb, int newRamMinMb) {
        return copy(port, bind, autoStart, displayName, serverId, newRamMb, newRamMinMb);
    }

    private FleetInstance copy(
            int newPort,
            String newBind,
            boolean newAuto,
            String newDisplay,
            String newServerId,
            int newRam,
            int newRamMin) {
        return new FleetInstance(
                id, newServerId, nodeId, relativeDir, newPort, newBind, newAuto, newDisplay,
                newRam, newRamMin);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("serverId", serverId);
        m.put("nodeId", nodeId);
        m.put("relativeDir", relativeDir);
        m.put("port", port);
        m.put("bind", bind);
        m.put("autoStart", autoStart);
        m.put("displayName", displayName);
        m.put("ramMb", ramMb);
        m.put("ramMinMb", ramMinMb);
        m.put("address", address());
        return Collections.unmodifiableMap(m);
    }

    @SuppressWarnings("unchecked")
    public static FleetInstance fromMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        String id = Objects.toString(m.get("id"), "").trim();
        if (id.isEmpty()) {
            return null;
        }
        return new FleetInstance(
                id,
                Objects.toString(m.getOrDefault("serverId", id), id),
                Objects.toString(m.getOrDefault("nodeId", "local"), "local"),
                Objects.toString(m.getOrDefault("relativeDir", "fleet/instances/" + id), ""),
                asInt(m.get("port"), 25566),
                Objects.toString(m.getOrDefault("bind", "127.0.0.1"), "127.0.0.1"),
                !Boolean.FALSE.equals(m.get("autoStart"))
                        && !"false".equalsIgnoreCase(String.valueOf(m.get("autoStart"))),
                Objects.toString(m.getOrDefault("displayName", id), id),
                asInt(m.get("ramMb"), RAM_INHERIT),
                asInt(m.get("ramMinMb"), RAM_INHERIT));
    }

    private static int clampRam(int mb) {
        if (mb <= 0) {
            return RAM_INHERIT;
        }
        return Math.min(131_072, Math.max(256, mb));
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("instance id required");
        }
        String cleaned = id.trim().toLowerCase();
        if (!cleaned.matches("[a-z0-9][a-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("invalid instance id: " + id);
        }
        return cleaned;
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
}

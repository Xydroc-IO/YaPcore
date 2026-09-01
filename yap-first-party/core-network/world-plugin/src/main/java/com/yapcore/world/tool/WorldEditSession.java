package com.yapcore.world.tool;

import org.bukkit.Material;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player GUI + tool preferences (material, brush radius, mode). */
public final class WorldEditSession {

    public enum Mode {
        SELECT, BRUSH
    }

    private static final ConcurrentHashMap<UUID, WorldEditSession> SESSIONS = new ConcurrentHashMap<>();

    private Mode mode = Mode.SELECT;
    private Material material = Material.STONE;
    private int brushRadius = 3;
    private String pendingSchemName;

    public static WorldEditSession of(UUID playerId) {
        return SESSIONS.computeIfAbsent(playerId, id -> new WorldEditSession());
    }

    public static void clear(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.SELECT : mode;
    }

    public void toggleMode() {
        mode = mode == Mode.SELECT ? Mode.BRUSH : Mode.SELECT;
    }

    public Material material() {
        return material;
    }

    public void setMaterial(Material material) {
        if (material != null && material.isBlock()) {
            this.material = material;
        }
    }

    public int brushRadius() {
        return brushRadius;
    }

    public void setBrushRadius(int brushRadius) {
        this.brushRadius = Math.max(1, brushRadius);
    }

    public void adjustRadius(int delta, int max) {
        brushRadius = Math.max(1, Math.min(max, brushRadius + delta));
    }

    public String pendingSchemName() {
        return pendingSchemName;
    }

    public void setPendingSchemName(String name) {
        this.pendingSchemName = name;
    }
}

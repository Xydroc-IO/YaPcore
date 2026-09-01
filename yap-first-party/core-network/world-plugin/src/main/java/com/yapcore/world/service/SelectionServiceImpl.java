package com.yapcore.world.service;

import com.yapcore.world.CuboidSelection;
import com.yapcore.world.SelectionService;
import com.yapcore.world.WorldConfig;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SelectionServiceImpl implements SelectionService {

    private WorldConfig config;
    private final Map<UUID, Pos1> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Pos2> pos2 = new ConcurrentHashMap<>();

    private record Pos1(String world, int x, int y, int z) {
    }

    private record Pos2(String world, int x, int y, int z) {
    }

    public SelectionServiceImpl(WorldConfig config) {
        this.config = config;
    }

    public void setConfig(WorldConfig config) {
        this.config = config;
    }

    @Override
    public Optional<CuboidSelection> selection(UUID playerUuid) {
        Pos1 a = pos1.get(playerUuid);
        Pos2 b = pos2.get(playerUuid);
        if (a == null || b == null || !a.world.equals(b.world)) {
            return Optional.empty();
        }
        CuboidSelection sel = new CuboidSelection(a.world, a.x, a.y, a.z, b.x, b.y, b.z);
        if (sel.volume() > config.maxVolume()) {
            return Optional.empty();
        }
        return Optional.of(sel);
    }

    public long volume(UUID playerUuid) {
        return selection(playerUuid).map(CuboidSelection::volume).orElse(0L);
    }

    @Override
    public void setPos1(UUID playerUuid, String world, int x, int y, int z) {
        pos1.put(playerUuid, new Pos1(world, x, y, z));
    }

    @Override
    public void setPos2(UUID playerUuid, String world, int x, int y, int z) {
        pos2.put(playerUuid, new Pos2(world, x, y, z));
    }

    @Override
    public void clearSelection(UUID playerUuid) {
        pos1.remove(playerUuid);
        pos2.remove(playerUuid);
    }

    public Optional<String> pos1Label(UUID playerUuid) {
        Pos1 p = pos1.get(playerUuid);
        if (p == null) {
            return Optional.empty();
        }
        return Optional.of(p.x + ", " + p.y + ", " + p.z + " (" + p.world + ")");
    }

    public Optional<String> pos2Label(UUID playerUuid) {
        Pos2 p = pos2.get(playerUuid);
        if (p == null) {
            return Optional.empty();
        }
        return Optional.of(p.x + ", " + p.y + ", " + p.z + " (" + p.world + ")");
    }

    public Optional<String> selectionIssue(UUID playerUuid) {
        Pos1 a = pos1.get(playerUuid);
        Pos2 b = pos2.get(playerUuid);
        if (a == null || b == null) {
            return Optional.of("Set pos1 and pos2");
        }
        if (!a.world.equals(b.world)) {
            return Optional.of("Pos1 and pos2 must be in the same world");
        }
        long vol = new CuboidSelection(a.world, a.x, a.y, a.z, b.x, b.y, b.z).volume();
        if (vol > config.maxVolume()) {
            return Optional.of("Volume " + vol + " exceeds limit " + config.maxVolume());
        }
        return Optional.empty();
    }

    public java.util.Map<String, Object> pos1Detail(UUID playerUuid) {
        Pos1 p = pos1.get(playerUuid);
        if (p == null) {
            return null;
        }
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("world", p.world);
        m.put("x", p.x);
        m.put("y", p.y);
        m.put("z", p.z);
        return m;
    }

    public java.util.Map<String, Object> pos2Detail(UUID playerUuid) {
        Pos2 p = pos2.get(playerUuid);
        if (p == null) {
            return null;
        }
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("world", p.world);
        m.put("x", p.x);
        m.put("y", p.y);
        m.put("z", p.z);
        return m;
    }

    public java.util.Map<String, Object> selectionBounds(UUID playerUuid) {
        return selection(playerUuid).map(sel -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("world", sel.world());
            m.put("minX", sel.minX());
            m.put("minY", sel.minY());
            m.put("minZ", sel.minZ());
            m.put("maxX", sel.maxX());
            m.put("maxY", sel.maxY());
            m.put("maxZ", sel.maxZ());
            m.put("sizeX", sel.maxX() - sel.minX() + 1);
            m.put("sizeY", sel.maxY() - sel.minY() + 1);
            m.put("sizeZ", sel.maxZ() - sel.minZ() + 1);
            return m;
        }).orElse(null);
    }
}

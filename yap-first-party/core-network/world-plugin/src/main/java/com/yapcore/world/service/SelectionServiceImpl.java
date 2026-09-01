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

    /** Expand selection by amount in facing directions (or all if dir null / "all"). */
    public Optional<CuboidSelection> expand(UUID playerUuid, int amount, String direction) {
        Optional<CuboidSelection> opt = selectionIgnoringVolume(playerUuid);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        CuboidSelection sel = opt.get();
        int minX = sel.minX();
        int minY = sel.minY();
        int maxX = sel.maxX();
        int maxY = sel.maxY();
        int minZ = sel.minZ();
        int maxZ = sel.maxZ();
        String dir = direction == null ? "all" : direction.toLowerCase();
        switch (dir) {
            case "up", "u" -> maxY += amount;
            case "down", "d" -> minY -= amount;
            case "north", "n" -> minZ -= amount;
            case "south", "s" -> maxZ += amount;
            case "east", "e" -> maxX += amount;
            case "west", "w" -> minX -= amount;
            case "vertical", "vert", "v" -> {
                maxY += amount;
                minY -= amount;
            }
            default -> {
                minX -= amount;
                maxX += amount;
                minY -= amount;
                maxY += amount;
                minZ -= amount;
                maxZ += amount;
            }
        }
        setPos1(playerUuid, sel.world(), minX, minY, minZ);
        setPos2(playerUuid, sel.world(), maxX, maxY, maxZ);
        return selection(playerUuid);
    }

    public Optional<CuboidSelection> contract(UUID playerUuid, int amount, String direction) {
        return expand(playerUuid, -Math.abs(amount), direction);
    }

    public Optional<CuboidSelection> shift(UUID playerUuid, int amount, String direction) {
        Optional<CuboidSelection> opt = selectionIgnoringVolume(playerUuid);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        CuboidSelection sel = opt.get();
        int dx = 0;
        int dy = 0;
        int dz = 0;
        switch (direction == null ? "" : direction.toLowerCase()) {
            case "up", "u" -> dy = amount;
            case "down", "d" -> dy = -amount;
            case "north", "n" -> dz = -amount;
            case "south", "s" -> dz = amount;
            case "east", "e" -> dx = amount;
            case "west", "w" -> dx = -amount;
            default -> {
                return Optional.empty();
            }
        }
        setPos1(playerUuid, sel.world(), sel.minX() + dx, sel.minY() + dy, sel.minZ() + dz);
        setPos2(playerUuid, sel.world(), sel.maxX() + dx, sel.maxY() + dy, sel.maxZ() + dz);
        return selection(playerUuid);
    }

    public Optional<CuboidSelection> outset(UUID playerUuid, int amount) {
        return expand(playerUuid, amount, "all");
    }

    public Optional<CuboidSelection> inset(UUID playerUuid, int amount) {
        return expand(playerUuid, -Math.abs(amount), "all");
    }

    public Optional<CuboidSelection> selectChunk(UUID playerUuid, String world, int blockX, int blockZ) {
        int minX = (blockX >> 4) << 4;
        int minZ = (blockZ >> 4) << 4;
        setPos1(playerUuid, world, minX, config == null ? -64 : -64, minZ);
        setPos2(playerUuid, world, minX + 15, 319, minZ + 15);
        return selectionIgnoringVolume(playerUuid);
    }

    /** Selection without max-volume gate (for morph feedback). */
    public Optional<CuboidSelection> selectionIgnoringVolume(UUID playerUuid) {
        Pos1 a = pos1.get(playerUuid);
        Pos2 b = pos2.get(playerUuid);
        if (a == null || b == null || !a.world.equals(b.world)) {
            return Optional.empty();
        }
        return Optional.of(new CuboidSelection(a.world, a.x, a.y, a.z, b.x, b.y, b.z));
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

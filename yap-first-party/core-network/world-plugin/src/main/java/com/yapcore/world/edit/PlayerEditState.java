package com.yapcore.world.edit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player FAWE-style toggles: //fast, tool mode, brush pattern, //limit, last-edit bounds. */
public final class PlayerEditState {

    public enum ToolMode {
        NONE,
        FARWAND,
        SUPER_SINGLE,
        SUPER_AREA,
        INFO,
        TREE
    }

    public record EditBounds(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private final Map<UUID, Boolean> fast = new ConcurrentHashMap<>();
    private final Map<UUID, ToolMode> tools = new ConcurrentHashMap<>();
    private final Map<UUID, String> brushPatterns = new ConcurrentHashMap<>();
    private final Map<UUID, String> treeTypes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> changeLimits = new ConcurrentHashMap<>();
    private final Map<UUID, EditBounds> lastEdit = new ConcurrentHashMap<>();

    public boolean isFast(UUID id) {
        return Boolean.TRUE.equals(fast.get(id));
    }

    public void setFast(UUID id, boolean value) {
        if (value) {
            fast.put(id, true);
        } else {
            fast.remove(id);
        }
    }

    public boolean toggleFast(UUID id) {
        boolean next = !isFast(id);
        setFast(id, next);
        return next;
    }

    public ToolMode tool(UUID id) {
        return tools.getOrDefault(id, ToolMode.NONE);
    }

    public void setTool(UUID id, ToolMode mode) {
        if (mode == null || mode == ToolMode.NONE) {
            tools.remove(id);
        } else {
            tools.put(id, mode);
        }
    }

    public String brushPattern(UUID id) {
        return brushPatterns.getOrDefault(id, "stone");
    }

    public void setBrushPattern(UUID id, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            brushPatterns.remove(id);
        } else {
            brushPatterns.put(id, pattern);
        }
    }

    public String treeType(UUID id) {
        return treeTypes.getOrDefault(id, "oak");
    }

    public void setTreeType(UUID id, String type) {
        treeTypes.put(id, type == null ? "oak" : type);
    }

    /** Session override for max changes; null = use config default. */
    public Long changeLimit(UUID id) {
        return changeLimits.get(id);
    }

    public void setChangeLimit(UUID id, Long limit) {
        if (limit == null || limit < 0) {
            changeLimits.remove(id);
        } else {
            changeLimits.put(id, limit);
        }
    }

    public long effectiveLimit(UUID id, long configDefault) {
        Long override = changeLimits.get(id);
        return override != null ? override : configDefault;
    }

    public void setLastEditBounds(UUID id, String world,
                                  int minX, int minY, int minZ,
                                  int maxX, int maxY, int maxZ) {
        lastEdit.put(id, new EditBounds(world, minX, minY, minZ, maxX, maxY, maxZ));
    }

    public EditBounds lastEditBounds(UUID id) {
        return lastEdit.get(id);
    }

    public void clear(UUID id) {
        fast.remove(id);
        tools.remove(id);
        brushPatterns.remove(id);
        treeTypes.remove(id);
        changeLimits.remove(id);
        lastEdit.remove(id);
    }
}

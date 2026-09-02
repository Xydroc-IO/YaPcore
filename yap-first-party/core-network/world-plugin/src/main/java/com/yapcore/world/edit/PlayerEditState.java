package com.yapcore.world.edit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player FAWE-style toggles: //fast, tool mode, brush pattern string. */
public final class PlayerEditState {

    public enum ToolMode {
        NONE,
        FARWAND,
        SUPER_SINGLE,
        SUPER_AREA,
        INFO,
        TREE
    }

    private final Map<UUID, Boolean> fast = new ConcurrentHashMap<>();
    private final Map<UUID, ToolMode> tools = new ConcurrentHashMap<>();
    private final Map<UUID, String> brushPatterns = new ConcurrentHashMap<>();
    private final Map<UUID, String> treeTypes = new ConcurrentHashMap<>();

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

    public void clear(UUID id) {
        fast.remove(id);
        tools.remove(id);
        brushPatterns.remove(id);
        treeTypes.remove(id);
    }
}

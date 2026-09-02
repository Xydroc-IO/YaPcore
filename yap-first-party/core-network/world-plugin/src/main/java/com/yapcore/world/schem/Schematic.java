package com.yapcore.world.schem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Schematic {

    public record BlockEntry(int dx, int dy, int dz, String encoded) {
    }

    /** Lightweight entity snapshot (type + relative pos + yaw/pitch). NBT payload optional. */
    public record EntityEntry(int dx, int dy, int dz, String type, float yaw, float pitch, String nbt) {
    }

    private final String world;
    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;
    private final List<BlockEntry> blocks;
    private final List<EntityEntry> entities;

    public Schematic(String world, int anchorX, int anchorY, int anchorZ, List<BlockEntry> blocks) {
        this(world, anchorX, anchorY, anchorZ, blocks, List.of());
    }

    public Schematic(String world, int anchorX, int anchorY, int anchorZ,
                     List<BlockEntry> blocks, List<EntityEntry> entities) {
        this.world = world;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.blocks = new ArrayList<>(blocks);
        this.entities = new ArrayList<>(entities == null ? List.of() : entities);
    }

    public String world() {
        return world;
    }

    public int anchorX() {
        return anchorX;
    }

    public int anchorY() {
        return anchorY;
    }

    public int anchorZ() {
        return anchorZ;
    }

    public List<BlockEntry> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    public List<EntityEntry> entities() {
        return Collections.unmodifiableList(entities);
    }

    public Bounds bounds() {
        if (blocks.isEmpty()) {
            return new Bounds(0, 0, 0);
        }
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        for (BlockEntry entry : blocks) {
            maxX = Math.max(maxX, entry.dx());
            maxY = Math.max(maxY, entry.dy());
            maxZ = Math.max(maxZ, entry.dz());
        }
        return new Bounds(maxX + 1, maxY + 1, maxZ + 1);
    }

    public record Bounds(int sizeX, int sizeY, int sizeZ) {
    }
}

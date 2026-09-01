package com.yapcore.world.schem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Schematic {

    public record BlockEntry(int dx, int dy, int dz, String encoded) {
    }

    private final String world;
    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;
    private final List<BlockEntry> blocks;

    public Schematic(String world, int anchorX, int anchorY, int anchorZ, List<BlockEntry> blocks) {
        this.world = world;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.blocks = new ArrayList<>(blocks);
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

    /** Bounding size from relative block offsets (1-based dimensions). */
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

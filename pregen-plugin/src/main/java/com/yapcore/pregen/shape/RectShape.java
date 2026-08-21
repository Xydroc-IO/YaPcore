package com.yapcore.pregen.shape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Inclusive chunk rectangle from block corners. */
public final class RectShape implements ChunkShape {

    private final List<ChunkPos> coords;
    private final String desc;

    public RectShape(int blockX1, int blockZ1, int blockX2, int blockZ2) {
        ChunkPos a = ChunkPos.fromBlock(blockX1, blockZ1);
        ChunkPos b = ChunkPos.fromBlock(blockX2, blockZ2);
        int minX = Math.min(a.x(), b.x());
        int maxX = Math.max(a.x(), b.x());
        int minZ = Math.min(a.z(), b.z());
        int maxZ = Math.max(a.z(), b.z());
        coords = new ArrayList<>((maxX - minX + 1) * (maxZ - minZ + 1));
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                coords.add(new ChunkPos(x, z));
            }
        }
        desc = "rect " + minX + "," + minZ + "→" + maxX + "," + maxZ;
    }

    @Override
    public long size() {
        return coords.size();
    }

    @Override
    public String description() {
        return desc;
    }

    @Override
    public Iterator<ChunkPos> iterator() {
        return coords.iterator();
    }
}

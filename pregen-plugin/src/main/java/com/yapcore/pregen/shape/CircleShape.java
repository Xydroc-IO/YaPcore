package com.yapcore.pregen.shape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Chunks whose centers lie within a block-radius circle. */
public final class CircleShape implements ChunkShape {

    private final List<ChunkPos> coords;
    private final String desc;

    public CircleShape(int centerBlockX, int centerBlockZ, int blockRadius) {
        int r = Math.max(0, blockRadius);
        double r2 = (double) r * r;
        ChunkPos c = ChunkPos.fromBlock(centerBlockX, centerBlockZ);
        int chunkPad = (r / 16) + 2;
        coords = new ArrayList<>();
        for (int x = c.x() - chunkPad; x <= c.x() + chunkPad; x++) {
            for (int z = c.z() - chunkPad; z <= c.z() + chunkPad; z++) {
                double midX = x * 16 + 8;
                double midZ = z * 16 + 8;
                double dx = midX - centerBlockX;
                double dz = midZ - centerBlockZ;
                if (dx * dx + dz * dz <= r2) {
                    coords.add(new ChunkPos(x, z));
                }
            }
        }
        desc = "circle r=" + r + " @" + centerBlockX + "," + centerBlockZ;
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

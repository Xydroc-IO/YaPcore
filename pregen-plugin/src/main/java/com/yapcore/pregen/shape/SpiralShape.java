package com.yapcore.pregen.shape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Expanding square spiral from center (radius in chunks, Chebyshev). */
public final class SpiralShape implements ChunkShape {

    private final int cx;
    private final int cz;
    private final int radiusChunks;
    private final List<ChunkPos> coords;

    public SpiralShape(int centerChunkX, int centerChunkZ, int radiusChunks) {
        this.cx = centerChunkX;
        this.cz = centerChunkZ;
        this.radiusChunks = Math.max(0, radiusChunks);
        this.coords = build();
    }

    private List<ChunkPos> build() {
        List<ChunkPos> out = new ArrayList<>();
        // Ring 0
        out.add(new ChunkPos(cx, cz));
        for (int r = 1; r <= radiusChunks; r++) {
            // top and bottom edges (include corners once)
            for (int x = cx - r; x <= cx + r; x++) {
                out.add(new ChunkPos(x, cz - r));
                out.add(new ChunkPos(x, cz + r));
            }
            // left and right edges (exclude corners already added)
            for (int z = cz - r + 1; z <= cz + r - 1; z++) {
                out.add(new ChunkPos(cx - r, z));
                out.add(new ChunkPos(cx + r, z));
            }
        }
        return out;
    }

    @Override
    public long size() {
        return coords.size();
    }

    @Override
    public String description() {
        return "spiral r=" + radiusChunks + " @" + cx + "," + cz;
    }

    @Override
    public Iterator<ChunkPos> iterator() {
        return coords.iterator();
    }
}

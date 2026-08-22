package com.yapcore.pregen.shape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Filled polygon over chunk cells (even-odd / ray cast on chunk centers). */
public final class PolygonShape implements ChunkShape {

    private final List<ChunkPos> coords;
    private final String desc;

    /** Vertices as block XZ pairs: x1,z1,x2,z2,... */
    public PolygonShape(int... blockXz) {
        if (blockXz == null || blockXz.length < 6 || blockXz.length % 2 != 0) {
            throw new IllegalArgumentException("polygon needs ≥3 vertices (x z pairs)");
        }
        int n = blockXz.length / 2;
        double[] vx = new double[n];
        double[] vz = new double[n];
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            vx[i] = blockXz[i * 2];
            vz[i] = blockXz[i * 2 + 1];
            minX = Math.min(minX, blockXz[i * 2]);
            maxX = Math.max(maxX, blockXz[i * 2]);
            minZ = Math.min(minZ, blockXz[i * 2 + 1]);
            maxZ = Math.max(maxZ, blockXz[i * 2 + 1]);
        }
        ChunkPos a = ChunkPos.fromBlock(minX, minZ);
        ChunkPos b = ChunkPos.fromBlock(maxX, maxZ);
        coords = new ArrayList<>();
        for (int cx = a.x(); cx <= b.x(); cx++) {
            for (int cz = a.z(); cz <= b.z(); cz++) {
                double px = cx * 16 + 8;
                double pz = cz * 16 + 8;
                if (contains(px, pz, vx, vz)) {
                    coords.add(new ChunkPos(cx, cz));
                }
            }
        }
        desc = "polygon verts=" + n + " chunks=" + coords.size();
    }

    static boolean contains(double x, double z, double[] vx, double[] vz) {
        boolean inside = false;
        int n = vx.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = vx[i], zi = vz[i];
            double xj = vx[j], zj = vz[j];
            boolean intersect = ((zi > z) != (zj > z))
                    && (x < (xj - xi) * (z - zi) / (zj - zi + 0.0) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
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

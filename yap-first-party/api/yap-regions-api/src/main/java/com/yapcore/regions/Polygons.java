package com.yapcore.regions;

import java.util.List;

/**
 * Pure 2D polygon helpers (XZ plane). No spatial index — O(n) ray-cast is fine for typical
 * admin-region vertex counts (tens of points, not thousands).
 */
public final class Polygons {

    private Polygons() {
    }

    /**
     * Ray-cast point-in-polygon on the XZ plane (odd-even rule).
     * Degenerate polygons (&lt; 3 vertices) never contain a point.
     */
    public static boolean contains(List<RegionVertex> vertices, int x, int z) {
        if (vertices == null || vertices.size() < 3) {
            return false;
        }
        boolean inside = false;
        int n = vertices.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int xi = vertices.get(i).x();
            int zi = vertices.get(i).z();
            int xj = vertices.get(j).x();
            int zj = vertices.get(j).z();
            boolean intersect = ((zi > z) != (zj > z))
                    && (x < (xj - xi) * (z - zi) / (double) (zj - zi + 0.0000001) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Absolute shoelace area in block² (may be 0 for collinear vertices). */
    public static double area(List<RegionVertex> vertices) {
        if (vertices == null || vertices.size() < 3) {
            return 0.0;
        }
        long sum = 0;
        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            RegionVertex a = vertices.get(i);
            RegionVertex b = vertices.get((i + 1) % n);
            sum += (long) a.x() * b.z() - (long) b.x() * a.z();
        }
        return Math.abs(sum) / 2.0;
    }
}

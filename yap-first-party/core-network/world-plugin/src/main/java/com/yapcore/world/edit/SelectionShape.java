package com.yapcore.world.edit;

import com.yapcore.world.CuboidSelection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Selection modes beyond cuboid: sphere, cylinder, polygon.
 * Bounding cuboid is always available for YaPRegions / Pregen.
 */
public final class SelectionShape {

    public enum Mode {
        CUBOID,
        SPHERE,
        CYL,
        POLY
    }

    public record Point(int x, int y, int z) {
    }

    private final ConcurrentHashMap<UUID, Mode> modes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<Point>> polyPoints = new ConcurrentHashMap<>();

    public Mode mode(UUID id) {
        return modes.getOrDefault(id, Mode.CUBOID);
    }

    public void setMode(UUID id, Mode mode) {
        modes.put(id, mode == null ? Mode.CUBOID : mode);
        if (mode != Mode.POLY) {
            polyPoints.remove(id);
        }
    }

    public boolean setMode(UUID id, String name) {
        if (name == null || name.isBlank()) {
            setMode(id, Mode.CUBOID);
            return true;
        }
        Mode m = switch (name.toLowerCase(Locale.ROOT)) {
            case "cuboid", "cube", "box" -> Mode.CUBOID;
            case "sphere", "ball", "ellipsoid" -> Mode.SPHERE;
            case "cyl", "cylinder", "disk" -> Mode.CYL;
            case "poly", "polygon", "polygonal" -> Mode.POLY;
            default -> null;
        };
        if (m == null) {
            return false;
        }
        setMode(id, m);
        return true;
    }

    public void addPolyPoint(UUID id, int x, int y, int z) {
        polyPoints.computeIfAbsent(id, k -> new ArrayList<>()).add(new Point(x, y, z));
    }

    public void clearPoly(UUID id) {
        polyPoints.remove(id);
    }

    public List<Point> polyPoints(UUID id) {
        return polyPoints.getOrDefault(id, List.of());
    }

    public void clear(UUID id) {
        modes.remove(id);
        polyPoints.remove(id);
    }

    /** Iterate blocks inside the active shape within the bounding cuboid. */
    public void forEach(UUID id, CuboidSelection sel, Consumer<int[]> consumer) {
        Mode mode = mode(id);
        switch (mode) {
            case SPHERE -> forEachSphere(sel, consumer);
            case CYL -> forEachCyl(sel, consumer);
            case POLY -> forEachPoly(id, sel, consumer);
            default -> BlockBatch.forEachBlock(sel.minX(), sel.minY(), sel.minZ(),
                    sel.maxX(), sel.maxY(), sel.maxZ(),
                    (x, y, z) -> consumer.accept(new int[]{x, y, z}));
        }
    }

    public boolean contains(UUID id, CuboidSelection sel, int x, int y, int z) {
        return switch (mode(id)) {
            case SPHERE -> inSphere(sel, x, y, z);
            case CYL -> inCyl(sel, x, y, z);
            case POLY -> inPoly(id, sel, x, y, z);
            default -> true;
        };
    }

    private static void forEachSphere(CuboidSelection sel, Consumer<int[]> consumer) {
        double cx = (sel.minX() + sel.maxX()) / 2.0;
        double cy = (sel.minY() + sel.maxY()) / 2.0;
        double cz = (sel.minZ() + sel.maxZ()) / 2.0;
        double rx = (sel.maxX() - sel.minX()) / 2.0 + 0.5;
        double ry = (sel.maxY() - sel.minY()) / 2.0 + 0.5;
        double rz = (sel.maxZ() - sel.minZ()) / 2.0 + 0.5;
        BlockBatch.forEachBlock(sel.minX(), sel.minY(), sel.minZ(), sel.maxX(), sel.maxY(), sel.maxZ(),
                (x, y, z) -> {
                    double nx = (x - cx) / rx;
                    double ny = (y - cy) / ry;
                    double nz = (z - cz) / rz;
                    if (nx * nx + ny * ny + nz * nz <= 1.0) {
                        consumer.accept(new int[]{x, y, z});
                    }
                });
    }

    private static boolean inSphere(CuboidSelection sel, int x, int y, int z) {
        double cx = (sel.minX() + sel.maxX()) / 2.0;
        double cy = (sel.minY() + sel.maxY()) / 2.0;
        double cz = (sel.minZ() + sel.maxZ()) / 2.0;
        double rx = (sel.maxX() - sel.minX()) / 2.0 + 0.5;
        double ry = (sel.maxY() - sel.minY()) / 2.0 + 0.5;
        double rz = (sel.maxZ() - sel.minZ()) / 2.0 + 0.5;
        double nx = (x - cx) / rx;
        double ny = (y - cy) / ry;
        double nz = (z - cz) / rz;
        return nx * nx + ny * ny + nz * nz <= 1.0;
    }

    private static void forEachCyl(CuboidSelection sel, Consumer<int[]> consumer) {
        double cx = (sel.minX() + sel.maxX()) / 2.0;
        double cz = (sel.minZ() + sel.maxZ()) / 2.0;
        double rx = (sel.maxX() - sel.minX()) / 2.0 + 0.5;
        double rz = (sel.maxZ() - sel.minZ()) / 2.0 + 0.5;
        BlockBatch.forEachBlock(sel.minX(), sel.minY(), sel.minZ(), sel.maxX(), sel.maxY(), sel.maxZ(),
                (x, y, z) -> {
                    double nx = (x - cx) / rx;
                    double nz = (z - cz) / rz;
                    if (nx * nx + nz * nz <= 1.0) {
                        consumer.accept(new int[]{x, y, z});
                    }
                });
    }

    private static boolean inCyl(CuboidSelection sel, int x, int y, int z) {
        double cx = (sel.minX() + sel.maxX()) / 2.0;
        double cz = (sel.minZ() + sel.maxZ()) / 2.0;
        double rx = (sel.maxX() - sel.minX()) / 2.0 + 0.5;
        double rz = (sel.maxZ() - sel.minZ()) / 2.0 + 0.5;
        double nx = (x - cx) / rx;
        double nz = (z - cz) / rz;
        return nx * nx + nz * nz <= 1.0;
    }

    private void forEachPoly(UUID id, CuboidSelection sel, Consumer<int[]> consumer) {
        List<Point> pts = polyPoints.get(id);
        if (pts == null || pts.size() < 3) {
            BlockBatch.forEachBlock(sel.minX(), sel.minY(), sel.minZ(), sel.maxX(), sel.maxY(), sel.maxZ(),
                    (x, y, z) -> consumer.accept(new int[]{x, y, z}));
            return;
        }
        BlockBatch.forEachBlock(sel.minX(), sel.minY(), sel.minZ(), sel.maxX(), sel.maxY(), sel.maxZ(),
                (x, y, z) -> {
                    if (pointInPoly(pts, x, z)) {
                        consumer.accept(new int[]{x, y, z});
                    }
                });
    }

    private boolean inPoly(UUID id, CuboidSelection sel, int x, int y, int z) {
        List<Point> pts = polyPoints.get(id);
        if (pts == null || pts.size() < 3) {
            return true;
        }
        return pointInPoly(pts, x, z);
    }

    /** Ray-cast 2D polygon inclusion on XZ. */
    private static boolean pointInPoly(List<Point> pts, int x, int z) {
        boolean inside = false;
        int n = pts.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int xi = pts.get(i).x();
            int zi = pts.get(i).z();
            int xj = pts.get(j).x();
            int zj = pts.get(j).z();
            boolean intersect = ((zi > z) != (zj > z))
                    && (x < (xj - xi) * (z - zi) / (double) (zj - zi + 0.0000001) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }
}

package com.yapcore.portals;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which blocks inside a portal cuboid are the walk-through.
 * {@code full} is the whole box; presets carve a mask; {@code custom} is painted blocks.
 */
public final class PortalShape {

    public enum Kind {
        FULL, FRAME, OVAL, RING, CROSS, ARCH, CUSTOM;

        public static Kind parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return FULL;
            }
            String s = raw.trim().toLowerCase(Locale.ROOT);
            return switch (s) {
                case "full", "box", "rect", "rectangle" -> FULL;
                case "frame", "border" -> FRAME;
                case "oval", "ellipse", "circle" -> OVAL;
                case "ring" -> RING;
                case "cross", "plus" -> CROSS;
                case "arch", "door", "doorway" -> ARCH;
                case "custom", "paint", "mask" -> CUSTOM;
                default -> FULL;
            };
        }

        public static boolean known(String raw) {
            if (raw == null || raw.isBlank()) {
                return false;
            }
            String s = raw.trim().toLowerCase(Locale.ROOT);
            return switch (s) {
                case "full", "box", "rect", "rectangle",
                     "frame", "border",
                     "oval", "ellipse", "circle", "ring",
                     "cross", "plus",
                     "arch", "door", "doorway",
                     "custom", "paint", "mask" -> true;
                default -> false;
            };
        }
    }

    private final Kind kind;
    /** Relative offsets from cuboid min, packed. Used only for {@link Kind#CUSTOM}. */
    private final Set<Long> blocks;

    private PortalShape(Kind kind, Set<Long> blocks) {
        this.kind = kind == null ? Kind.FULL : kind;
        this.blocks = blocks == null || blocks.isEmpty() ? Set.of() : Set.copyOf(blocks);
    }

    public static PortalShape full() {
        return new PortalShape(Kind.FULL, Set.of());
    }

    public static PortalShape of(Kind kind) {
        if (kind == null || kind == Kind.FULL) {
            return full();
        }
        return new PortalShape(kind, Set.of());
    }

    public static PortalShape custom(Collection<long[]> triples) {
        Set<Long> packed = new LinkedHashSet<>();
        if (triples != null) {
            for (long[] t : triples) {
                if (t != null && t.length >= 3) {
                    packed.add(pack((int) t[0], (int) t[1], (int) t[2]));
                }
            }
        }
        return new PortalShape(Kind.CUSTOM, packed);
    }

    public static PortalShape customPacked(Collection<Long> packed) {
        return new PortalShape(Kind.CUSTOM, packed == null ? Set.of() : new LinkedHashSet<>(packed));
    }

    public Kind kind() {
        return kind;
    }

    public int customCount() {
        return blocks.size();
    }

    public boolean contains(PortalCuboid box, int x, int y, int z) {
        if (box == null || !box.containsBlock(x, y, z)) {
            return false;
        }
        int dx = x - box.minX();
        int dy = y - box.minY();
        int dz = z - box.minZ();
        int w = box.maxX() - box.minX();
        int h = box.maxY() - box.minY();
        int d = box.maxZ() - box.minZ();
        return switch (kind) {
            case FULL -> true;
            case FRAME -> frame(dx, dy, dz, w, h, d);
            case OVAL -> oval(dx, dy, dz, w, h, d);
            case RING -> ring(dx, dy, dz, w, h, d);
            case CROSS -> cross(dx, dy, dz, w, h, d);
            case ARCH -> arch(dx, dy, dz, w, h, d);
            case CUSTOM -> blocks.contains(pack(dx, dy, dz));
        };
    }

    /** Rough size for “smallest portal wins” when volumes overlap. */
    public int estimateCount(PortalCuboid box) {
        if (kind == Kind.CUSTOM) {
            return Math.max(1, blocks.size());
        }
        if (kind == Kind.FULL) {
            return Math.max(1, box.volumeBlocks());
        }
        return Math.max(1, box.volumeBlocks() / 2);
    }

    public PortalShape withBlock(int dx, int dy, int dz, boolean add) {
        Set<Long> next = new LinkedHashSet<>(blocks);
        long key = pack(dx, dy, dz);
        if (add) {
            next.add(key);
        } else {
            next.remove(key);
        }
        return new PortalShape(Kind.CUSTOM, next);
    }

    public List<int[]> customBlocks() {
        if (blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream().map(PortalShape::unpack).toList();
    }

    public static long pack(int dx, int dy, int dz) {
        return ((long) (dx & 0x3FF) << 20) | ((long) (dy & 0x3FF) << 10) | (dz & 0x3FFL);
    }

    public static int[] unpack(long packed) {
        return new int[] {
                (int) ((packed >> 20) & 0x3FF),
                (int) ((packed >> 10) & 0x3FF),
                (int) (packed & 0x3FF)
        };
    }

    /**
     * One-block border of the portal face. A two-block-deep doorway stays a flat frame,
     * not a solid slab: the thin axis is thickness and is filled through.
     */
    static boolean frame(int dx, int dy, int dz, int w, int h, int d) {
        boolean[] depth = depthAxes(w, h, d);
        int[] span = {w, h, d};
        int[] pos = {dx, dy, dz};
        int face = 0;
        int onRim = 0;
        for (int i = 0; i < 3; i++) {
            if (depth[i] || span[i] <= 0) {
                continue;
            }
            face++;
            if (pos[i] == 0 || pos[i] == span[i]) {
                onRim++;
            }
        }
        return face <= 1 || onRim >= 1;
    }

    /** Ellipse through the midpoints of the face. Thickness is extruded, not a third radius. */
    static boolean oval(int dx, int dy, int dz, int w, int h, int d) {
        return ellipse(dx, dy, dz, w, h, d, depthAxes(w, h, d));
    }

    /** Oval outline, one block thick, so {@code ring} is not a square frame. */
    static boolean ring(int dx, int dy, int dz, int w, int h, int d) {
        boolean[] depth = depthAxes(w, h, d);
        if (!ellipse(dx, dy, dz, w, h, d, depth)) {
            return false;
        }
        int[] span = {w, h, d};
        int[] pos = {dx, dy, dz};
        boolean hole = false;
        int[] innerSpan = new int[3];
        int[] innerPos = new int[3];
        for (int i = 0; i < 3; i++) {
            if (depth[i] || span[i] <= 2) {
                innerSpan[i] = span[i];
                innerPos[i] = pos[i];
                continue;
            }
            hole = true;
            innerSpan[i] = span[i] - 2;
            innerPos[i] = pos[i] - 1;
        }
        if (!hole) {
            return true;
        }
        return !ellipse(innerPos[0], innerPos[1], innerPos[2], innerSpan[0], innerSpan[1], innerSpan[2], depth);
    }

    /** Plus on the face. Even widths use both middle blocks so the cross stays centered. */
    static boolean cross(int dx, int dy, int dz, int w, int h, int d) {
        boolean[] depth = depthAxes(w, h, d);
        int[] span = {w, h, d};
        int[] pos = {dx, dy, dz};
        int face = 0;
        int longest = 0;
        for (int i = 0; i < 3; i++) {
            if (depth[i] || span[i] <= 0) {
                continue;
            }
            face++;
            longest = Math.max(longest, span[i]);
        }
        if (face <= 1) {
            return true;
        }
        int arm = Math.max(1, (longest + 1) / 7);
        double reach = (arm - 1) / 2.0 + 0.51;
        int onArm = 0;
        for (int i = 0; i < 3; i++) {
            if (depth[i] || span[i] <= 0) {
                continue;
            }
            if (Math.abs(pos[i] - span[i] / 2.0) <= reach) {
                onArm++;
            }
        }
        return onArm >= Math.max(1, face - 1);
    }

    /**
     * Roman arch: rectangular stem, then a semicircle whose diameter is the doorway width.
     * Up is Y when the portal has height, so a wide door still arches upward.
     */
    static boolean arch(int dx, int dy, int dz, int w, int h, int d) {
        boolean[] depth = depthAxes(w, h, d);
        int[] span = {w, h, d};
        int[] pos = {dx, dy, dz};
        int heightAxis = heightAxis(span, depth);
        int widthAxis = widthAxis(heightAxis, span, depth);
        int uw = span[widthAxis];
        int vh = span[heightAxis];
        if (uw <= 0 || vh <= 0) {
            return true;
        }
        int u = pos[widthAxis];
        int v = pos[heightAxis];
        double radius = uw / 2.0;
        double spring = vh - radius;
        if (v <= spring) {
            return true;
        }
        double relU = u - radius;
        double relV = v - spring;
        return relU * relU + relV * relV <= radius * radius;
    }

    /**
     * Axes that are just the glass thickness. A doorway two blocks deep must not become a 3D blob.
     */
    private static boolean[] depthAxes(int w, int h, int d) {
        int[] span = {w, h, d};
        int max = Math.max(w, Math.max(h, d));
        boolean[] depth = new boolean[3];
        if (max < 3) {
            return depth;
        }
        for (int i = 0; i < 3; i++) {
            if (span[i] == 0 || (span[i] <= 2 && span[i] * 2 < max)) {
                depth[i] = true;
            }
        }
        int face = 0;
        for (int i = 0; i < 3; i++) {
            if (!depth[i] && span[i] > 0) {
                face++;
            }
        }
        if (face == 0) {
            int keep = 0;
            for (int i = 1; i < 3; i++) {
                if (span[i] > span[keep]) {
                    keep = i;
                }
            }
            depth[keep] = false;
        }
        return depth;
    }

    private static boolean ellipse(int dx, int dy, int dz, int w, int h, int d, boolean[] depth) {
        int[] span = {w, h, d};
        int[] pos = {dx, dy, dz};
        double sum = 0;
        int terms = 0;
        for (int i = 0; i < 3; i++) {
            if (depth[i] || span[i] <= 0) {
                continue;
            }
            double radius = span[i] / 2.0;
            double delta = pos[i] - radius;
            sum += (delta * delta) / (radius * radius);
            terms++;
        }
        return terms == 0 || sum <= 1.0;
    }

    private static int heightAxis(int[] span, boolean[] depth) {
        if (!depth[1] && span[1] > 0) {
            return 1;
        }
        int best = 0;
        for (int i = 1; i < 3; i++) {
            if (!depth[i] && span[i] > span[best]) {
                best = i;
            }
        }
        return best;
    }

    private static int widthAxis(int heightAxis, int[] span, boolean[] depth) {
        int best = heightAxis == 0 ? 1 : 0;
        for (int i = 0; i < 3; i++) {
            if (i == heightAxis || depth[i]) {
                continue;
            }
            if (span[i] > span[best] || depth[best] || best == heightAxis) {
                best = i;
            }
        }
        return best;
    }
}

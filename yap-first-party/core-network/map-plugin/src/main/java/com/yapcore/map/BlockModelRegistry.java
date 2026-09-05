package com.yapcore.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recognizable (not full blockstate) multi-box models for common non-cubes.
 * Pure logic — classify by material name / packed state; no Bukkit types required.
 *
 * <p>Packed state bits (kind-specific):
 * <ul>
 *   <li>STAIRS: facing 0–3 (N/E/S/W), half bit4 (0 bottom / 1 top), shape bits5–6 (0 straight)</li>
 *   <li>SLAB: 0 bottom, 1 top, 2 double</li>
 *   <li>FENCE/WALL: connection bits N=1 E=2 S=4 W=8</li>
 *   <li>CARPET: unused</li>
 * </ul>
 */
public final class BlockModelRegistry {

    public enum Kind {
        CUBE,
        STAIRS,
        SLAB,
        CARPET,
        FENCE,
        WALL
    }

    /** One AABB relative to the block origin, in block units (0–1 typical). */
    public record ModelBox(float ox, float oy, float oz, float sx, float sy, float sz) {
        public ModelBox {
            if (sx <= 0 || sy <= 0 || sz <= 0) {
                throw new IllegalArgumentException("box extents must be positive");
            }
        }
    }

    public static final int FACING_NORTH = 0;
    public static final int FACING_EAST = 1;
    public static final int FACING_SOUTH = 2;
    public static final int FACING_WEST = 3;
    public static final int SLAB_BOTTOM = 0;
    public static final int SLAB_TOP = 1;
    public static final int SLAB_DOUBLE = 2;
    public static final int CONN_NORTH = 1;
    public static final int CONN_EAST = 2;
    public static final int CONN_SOUTH = 4;
    public static final int CONN_WEST = 8;

    private BlockModelRegistry() {
    }

    public static Kind classify(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return Kind.CUBE;
        }
        String n = materialName.toUpperCase(Locale.ROOT);
        if (n.endsWith("_STAIRS") || "STAIRS".equals(n)) {
            return Kind.STAIRS;
        }
        if (n.endsWith("_SLAB") || "SLAB".equals(n)) {
            return Kind.SLAB;
        }
        if (n.endsWith("_CARPET") || "MOSS_CARPET".equals(n) || "CARPET".equals(n)) {
            return Kind.CARPET;
        }
        if (n.endsWith("_FENCE") && !n.endsWith("_FENCE_GATE")) {
            return Kind.FENCE;
        }
        if (n.endsWith("_WALL") && !n.contains("SIGN") && !n.contains("BANNER") && !n.contains("HEAD")) {
            return Kind.WALL;
        }
        return Kind.CUBE;
    }

    public static boolean isSpecial(Kind kind) {
        return kind != null && kind != Kind.CUBE;
    }

    public static boolean isSpecial(String materialName) {
        return isSpecial(classify(materialName));
    }

    /** Pack stairs facing + half (bottom=0 / top=1). Shape ignored for simplified model. */
    public static int packStairs(int facing, boolean topHalf) {
        return (facing & 3) | (topHalf ? 4 : 0);
    }

    public static int packSlab(int type) {
        return Math.max(0, Math.min(2, type));
    }

    public static int packFence(boolean north, boolean east, boolean south, boolean west) {
        int s = 0;
        if (north) {
            s |= CONN_NORTH;
        }
        if (east) {
            s |= CONN_EAST;
        }
        if (south) {
            s |= CONN_SOUTH;
        }
        if (west) {
            s |= CONN_WEST;
        }
        return s;
    }

    /**
     * Emit simplified extruded / multi-box shapes for one block.
     *
     * @return empty when kind is {@link Kind#CUBE} (caller should use full cube / greedy path)
     */
    public static ModelBox[] boxes(Kind kind, int state) {
        if (kind == null || kind == Kind.CUBE) {
            return new ModelBox[0];
        }
        return switch (kind) {
            case STAIRS -> stairs(state);
            case SLAB -> slab(state);
            case CARPET -> new ModelBox[] {new ModelBox(0f, 0f, 0f, 1f, 1f / 16f, 1f)};
            case FENCE -> fence(state);
            case WALL -> wall(state);
            default -> new ModelBox[0];
        };
    }

    public static ModelBox[] boxes(String materialName, int state) {
        return boxes(classify(materialName), state);
    }

    /**
     * Append model boxes into a packed milliblock buffer (stride 7).
     *
     * @return number of ints written
     */
    public static int appendPacked(int[] buf, int offset, int localX, int worldY, int localZ,
                                   Kind kind, int state, int rgb) {
        ModelBox[] boxes = boxes(kind, state);
        int n = offset;
        int baseX = MeshUnits.ofBlocks(localX);
        int baseY = MeshUnits.ofBlocks(worldY);
        int baseZ = MeshUnits.ofBlocks(localZ);
        int color = rgb & 0xffffff;
        for (ModelBox b : boxes) {
            if (n + GreedyMesher.STRIDE > buf.length) {
                break;
            }
            buf[n++] = baseX + MeshUnits.of(b.ox());
            buf[n++] = baseY + MeshUnits.of(b.oy());
            buf[n++] = baseZ + MeshUnits.of(b.oz());
            buf[n++] = MeshUnits.of(b.sx());
            buf[n++] = MeshUnits.of(b.sy());
            buf[n++] = MeshUnits.of(b.sz());
            buf[n++] = color;
        }
        return n - offset;
    }

    private static ModelBox[] stairs(int state) {
        int facing = state & 3;
        boolean top = (state & 4) != 0;
        List<ModelBox> out = new ArrayList<>(2);
        if (top) {
            out.add(new ModelBox(0f, 0.5f, 0f, 1f, 0.5f, 1f));
            out.add(stairStep(facing, 0f));
        } else {
            out.add(new ModelBox(0f, 0f, 0f, 1f, 0.5f, 1f));
            out.add(stairStep(facing, 0.5f));
        }
        return out.toArray(new ModelBox[0]);
    }

    private static ModelBox stairStep(int facing, float y) {
        return switch (facing) {
            case FACING_EAST -> new ModelBox(0.5f, y, 0f, 0.5f, 0.5f, 1f);
            case FACING_SOUTH -> new ModelBox(0f, y, 0.5f, 1f, 0.5f, 0.5f);
            case FACING_WEST -> new ModelBox(0f, y, 0f, 0.5f, 0.5f, 1f);
            default -> new ModelBox(0f, y, 0f, 1f, 0.5f, 0.5f); // north
        };
    }

    private static ModelBox[] slab(int state) {
        int type = packSlab(state);
        if (type == SLAB_DOUBLE) {
            return new ModelBox[] {new ModelBox(0f, 0f, 0f, 1f, 1f, 1f)};
        }
        if (type == SLAB_TOP) {
            return new ModelBox[] {new ModelBox(0f, 0.5f, 0f, 1f, 0.5f, 1f)};
        }
        return new ModelBox[] {new ModelBox(0f, 0f, 0f, 1f, 0.5f, 1f)};
    }

    private static ModelBox[] fence(int state) {
        List<ModelBox> out = new ArrayList<>(5);
        // Center post ~6/16
        float p0 = 6f / 16f;
        float p1 = 10f / 16f;
        float pw = p1 - p0;
        out.add(new ModelBox(p0, 0f, p0, pw, 1f, pw));
        float barY = 6f / 16f;
        float barH = 3f / 16f;
        float barY2 = 12f / 16f;
        if ((state & CONN_NORTH) != 0) {
            out.add(new ModelBox(p0, barY, 0f, pw, barH, p0));
            out.add(new ModelBox(p0, barY2, 0f, pw, barH, p0));
        }
        if ((state & CONN_SOUTH) != 0) {
            out.add(new ModelBox(p0, barY, p1, pw, barH, 1f - p1));
            out.add(new ModelBox(p0, barY2, p1, pw, barH, 1f - p1));
        }
        if ((state & CONN_WEST) != 0) {
            out.add(new ModelBox(0f, barY, p0, p0, barH, pw));
            out.add(new ModelBox(0f, barY2, p0, p0, barH, pw));
        }
        if ((state & CONN_EAST) != 0) {
            out.add(new ModelBox(p1, barY, p0, 1f - p1, barH, pw));
            out.add(new ModelBox(p1, barY2, p0, 1f - p1, barH, pw));
        }
        return out.toArray(new ModelBox[0]);
    }

    private static ModelBox[] wall(int state) {
        List<ModelBox> out = new ArrayList<>(5);
        float c0 = 4f / 16f;
        float c1 = 12f / 16f;
        float cw = c1 - c0;
        out.add(new ModelBox(c0, 0f, c0, cw, 1f, cw));
        if ((state & CONN_NORTH) != 0) {
            out.add(new ModelBox(c0, 0f, 0f, cw, 13f / 16f, c0));
        }
        if ((state & CONN_SOUTH) != 0) {
            out.add(new ModelBox(c0, 0f, c1, cw, 13f / 16f, 1f - c1));
        }
        if ((state & CONN_WEST) != 0) {
            out.add(new ModelBox(0f, 0f, c0, c0, 13f / 16f, cw));
        }
        if ((state & CONN_EAST) != 0) {
            out.add(new ModelBox(c1, 0f, c0, 1f - c1, 13f / 16f, cw));
        }
        return out.toArray(new ModelBox[0]);
    }
}

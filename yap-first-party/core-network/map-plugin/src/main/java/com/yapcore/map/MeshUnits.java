package com.yapcore.map;

/**
 * Geometric units for packed v2 mesh boxes.
 *
 * <p>Positions and sizes are stored as milliblocks ({@link #UNIT} = 1.0 block) so
 * slabs/carpets/fences can use fractional extents while keeping an {@code int[]} buffer.
 */
public final class MeshUnits {

    /** Milliblocks per full block. */
    public static final int UNIT = 1000;

    private MeshUnits() {
    }

    public static int of(float blocks) {
        return Math.round(blocks * UNIT);
    }

    public static int ofBlocks(int blocks) {
        return blocks * UNIT;
    }

    public static float toBlocks(int milli) {
        return milli / (float) UNIT;
    }
}

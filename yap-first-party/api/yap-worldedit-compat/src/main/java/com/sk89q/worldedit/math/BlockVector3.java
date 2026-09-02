package com.sk89q.worldedit.math;

public final class BlockVector3 {

    private final int x;
    private final int y;
    private final int z;

    private BlockVector3(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockVector3 at(int x, int y, int z) {
        return new BlockVector3(x, y, z);
    }

    public static BlockVector3 zero() {
        return at(0, 0, 0);
    }

    public int x() {
        return x;
    }

    public int getX() {
        return x;
    }

    public int y() {
        return y;
    }

    public int getY() {
        return y;
    }

    public int z() {
        return z;
    }

    public int getZ() {
        return z;
    }

    public BlockVector3 add(int dx, int dy, int dz) {
        return at(x + dx, y + dy, z + dz);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}

package com.sk89q.worldedit.regions;

import com.sk89q.worldedit.math.BlockVector3;

public final class CuboidRegion implements Region {

    private final BlockVector3 min;
    private final BlockVector3 max;

    public CuboidRegion(BlockVector3 a, BlockVector3 b) {
        int minX = Math.min(a.x(), b.x());
        int minY = Math.min(a.y(), b.y());
        int minZ = Math.min(a.z(), b.z());
        int maxX = Math.max(a.x(), b.x());
        int maxY = Math.max(a.y(), b.y());
        int maxZ = Math.max(a.z(), b.z());
        this.min = BlockVector3.at(minX, minY, minZ);
        this.max = BlockVector3.at(maxX, maxY, maxZ);
    }

    @Override
    public BlockVector3 getMinimumPoint() {
        return min;
    }

    @Override
    public BlockVector3 getMaximumPoint() {
        return max;
    }

    @Override
    public long getVolume() {
        return (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
    }
}

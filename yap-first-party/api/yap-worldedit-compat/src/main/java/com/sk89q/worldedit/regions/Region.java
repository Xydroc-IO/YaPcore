package com.sk89q.worldedit.regions;

import com.sk89q.worldedit.math.BlockVector3;

public interface Region {
    BlockVector3 getMinimumPoint();

    BlockVector3 getMaximumPoint();

    long getVolume();
}

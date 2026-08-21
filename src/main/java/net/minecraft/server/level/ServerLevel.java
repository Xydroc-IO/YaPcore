package net.minecraft.server.level;

import net.minecraft.world.level.Level;

/** NMS server level / world handle. */
public final class ServerLevel extends Level {

    public ServerLevel(String dimensionKey) {
        super(dimensionKey);
    }

    public String dimension() {
        return getDimensionKey();
    }
}

package com.yapcore.yap420.press;

import java.util.UUID;

/** Placed packaging press (barrier hitbox + BlockDisplays). */
public final class PressState {

    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final UUID entityUuid;

    public PressState(String world, int x, int y, int z, UUID entityUuid) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.entityUuid = entityUuid;
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public PressState withEntity(UUID uuid) {
        return new PressState(world, x, y, z, uuid);
    }

    public String key() {
        return keyOf(world, x, y, z);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public static String keyOf(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}

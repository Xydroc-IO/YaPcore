package com.yapcore.playerdata.db;

import java.util.UUID;

/** Shared location row for homes/warps. */
public final class LocationRow {
    private final String name;
    private final String serverId;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final UUID owner;

    public LocationRow(String name, String serverId, String world,
                       double x, double y, double z, float yaw, float pitch, UUID owner) {
        this.name = name;
        this.serverId = serverId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.owner = owner;
    }

    public String name() {
        return name;
    }

    public String serverId() {
        return serverId;
    }

    public String world() {
        return world;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public UUID owner() {
        return owner;
    }
}

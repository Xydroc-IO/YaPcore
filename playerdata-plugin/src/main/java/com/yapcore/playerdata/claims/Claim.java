package com.yapcore.playerdata.claims;

import java.util.UUID;

/** Rectangular claim on one server/world (XZ plane, full height). */
public final class Claim {
    private final long id;
    private final UUID owner;
    private final String serverId;
    private final String world;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private String name;

    public Claim(long id, UUID owner, String serverId, String world,
                 int minX, int maxX, int minZ, int maxZ, String name) {
        this.id = id;
        this.owner = owner;
        this.serverId = serverId;
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
        this.name = name;
    }

    public long id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String serverId() {
        return serverId;
    }

    public String world() {
        return world;
    }

    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int length() {
        return maxZ - minZ + 1;
    }

    public int area() {
        return width() * length();
    }

    public boolean contains(String worldName, int x, int z) {
        return world.equals(worldName) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean overlaps(String worldName, int minX, int maxX, int minZ, int maxZ) {
        if (!world.equals(worldName)) {
            return false;
        }
        int aMinX = Math.min(minX, maxX);
        int aMaxX = Math.max(minX, maxX);
        int aMinZ = Math.min(minZ, maxZ);
        int aMaxZ = Math.max(minZ, maxZ);
        return this.minX <= aMaxX && this.maxX >= aMinX && this.minZ <= aMaxZ && this.maxZ >= aMinZ;
    }
}

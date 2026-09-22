package com.yapcore.claims;

import java.util.UUID;

/** Rectangular claim (or subclaim) on one server/world, with optional Y bounds. */
public final class Claim {
    private final long id;
    private final UUID owner;
    private final String serverId;
    private final String world;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int minY;
    private final int maxY;
    private final Long parentId;
    private String name;
    private double taxDue;
    private boolean taxFrozen;

    public Claim(long id, UUID owner, String serverId, String world,
                 int minX, int maxX, int minZ, int maxZ, String name,
                 Long parentId, double taxDue, boolean taxFrozen) {
        this(id, owner, serverId, world, minX, maxX, minZ, maxZ,
                Integer.MIN_VALUE / 4, Integer.MAX_VALUE / 4, name, parentId, taxDue, taxFrozen);
    }

    public Claim(long id, UUID owner, String serverId, String world,
                 int minX, int maxX, int minZ, int maxZ,
                 int minY, int maxY, String name,
                 Long parentId, double taxDue, boolean taxFrozen) {
        this.id = id;
        this.owner = owner;
        this.serverId = serverId;
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        this.name = name;
        this.parentId = parentId;
        this.taxDue = taxDue;
        this.taxFrozen = taxFrozen;
    }

    public static Claim topLevel(long id, UUID owner, String serverId, String world,
                                 int minX, int maxX, int minZ, int maxZ, String name) {
        return new Claim(id, owner, serverId, world, minX, maxX, minZ, maxZ, name, null, 0, false);
    }

    public static Claim topLevel(long id, UUID owner, String serverId, String world,
                                 int minX, int maxX, int minZ, int maxZ,
                                 int minY, int maxY, String name) {
        return new Claim(id, owner, serverId, world, minX, maxX, minZ, maxZ, minY, maxY,
                name, null, 0, false);
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

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public Long parentId() {
        return parentId;
    }

    public boolean isSubdivision() {
        return parentId != null;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double taxDue() {
        return taxDue;
    }

    public void setTaxDue(double taxDue) {
        this.taxDue = taxDue;
    }

    public boolean taxFrozen() {
        return taxFrozen;
    }

    public void setTaxFrozen(boolean taxFrozen) {
        this.taxFrozen = taxFrozen;
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

    public boolean contains(String worldName, int x, int y, int z) {
        return contains(worldName, x, z) && y >= minY && y <= maxY;
    }

    public boolean containsFully(int minX, int maxX, int minZ, int maxZ) {
        int aMinX = Math.min(minX, maxX);
        int aMaxX = Math.max(minX, maxX);
        int aMinZ = Math.min(minZ, maxZ);
        int aMaxZ = Math.max(minZ, maxZ);
        return aMinX >= this.minX && aMaxX <= this.maxX && aMinZ >= this.minZ && aMaxZ <= this.maxZ;
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

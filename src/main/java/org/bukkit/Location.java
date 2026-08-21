package org.bukkit;

public final class Location {
    private World world;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    public Location(String world, double x, double y, double z) {
        this(world, x, y, z, 0f, 0f);
    }

    public Location(String world, double x, double y, double z, float yaw, float pitch) {
        this.worldName = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Location(World world, double x, double y, double z) {
        this(world, x, y, z, 0f, 0f);
    }

    public Location(World world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.worldName = world != null ? world.getName() : "world";
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
        if (world != null) {
            this.worldName = world.getName();
        }
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getBlockX() {
        return (int) Math.floor(x);
    }

    public int getBlockY() {
        return (int) Math.floor(y);
    }

    public int getBlockZ() {
        return (int) Math.floor(z);
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public Location clone() {
        Location c = new Location(worldName, x, y, z, yaw, pitch);
        c.world = world;
        return c;
    }

    @Override
    public String toString() {
        return "Location{world=" + worldName + ",x=" + x + ",y=" + y + ",z=" + z + "}";
    }
}

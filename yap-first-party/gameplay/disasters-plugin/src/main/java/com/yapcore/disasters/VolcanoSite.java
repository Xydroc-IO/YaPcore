package com.yapcore.disasters;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Named soft-volcano crater (existing terrain; no worldgen). */
public final class VolcanoSite {

    private final String id;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final boolean dormant;

    public VolcanoSite(String id, String worldName, double x, double y, double z, boolean dormant) {
        this.id = id;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dormant = dormant;
    }

    public String id() {
        return id;
    }

    public String worldName() {
        return worldName;
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

    public boolean dormant() {
        return dormant;
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    public String describe() {
        return id + " @ " + worldName + " " + (int) x + "," + (int) y + "," + (int) z
                + (dormant ? " (dormant)" : "");
    }
}

package com.sk89q.worldedit.bukkit;

import com.sk89q.worldedit.world.World;

public final class BukkitWorld implements World {

    private final org.bukkit.World world;

    public BukkitWorld(org.bukkit.World world) {
        this.world = world;
    }

    public org.bukkit.World getWorld() {
        return world;
    }

    @Override
    public String getName() {
        return world.getName();
    }
}

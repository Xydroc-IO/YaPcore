package org.bukkit.block;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/** Block handle — mutations must go through SYNC bridge. */
public interface Block {

    Material getType();

    void setType(Material type);

    World getWorld();

    int getX();

    int getY();

    int getZ();

    Location getLocation();
}

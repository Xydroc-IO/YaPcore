package org.bukkit;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** World handle — block/entity changes require SYNC. */
public interface World {

    String getName();

    UUID getUID();

    Block getBlockAt(int x, int y, int z);

    Block getBlockAt(Location location);

    Location getSpawnLocation();

    void setSpawnLocation(Location location);

    List<Player> getPlayers();

    long getTime();

    void setTime(long time);
}

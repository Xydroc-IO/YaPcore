package org.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.UUID;

/**
 * Minimal Bukkit Entity — enough for command sources and Craft casts.
 */
public interface Entity extends CommandSender {

    UUID getUniqueId();

    Location getLocation();

    World getWorld();

    void teleport(Location location);

    boolean isValid();

    boolean isDead();
}

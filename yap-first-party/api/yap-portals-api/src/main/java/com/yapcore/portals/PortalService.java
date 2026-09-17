package com.yapcore.portals;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

/** Folia-side fleet portal registry and transfer API. */
public interface PortalService {

    Collection<Portal> list();

    Optional<Portal> get(String name);

    Optional<Portal> at(Location location);

    Portal define(String name, String world, PortalCuboid cuboid, String targetServer);

    boolean remove(String name);

    void save(Portal portal);

    void reload();

    /** Transfer via YaP Link BungeeCord {@code Connect} (or same-server no-op message). */
    boolean transfer(Player player, String targetServer);

    boolean transfer(Player player, Portal portal);
}

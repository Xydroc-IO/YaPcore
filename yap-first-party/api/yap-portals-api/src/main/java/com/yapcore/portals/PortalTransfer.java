package com.yapcore.portals;

import org.bukkit.entity.Player;

/**
 * Soft-dep transfer hook for hub NPCs and other plugins.
 * Implemented by YaPPortals; sends BungeeCord {@code Connect} through YaP Link.
 */
public interface PortalTransfer {

    /**
     * Request a fleet transfer to {@code targetServer} (Link {@code servers.*} id).
     *
     * @return true if the connect packet was queued
     */
    boolean transfer(Player player, String targetServer);
}

package com.yapcore.playerdata;

import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Soft API for home teleports (YaPPortals home pads, kits, etc.).
 * Registered by YaPPlayerData when {@code features.homes} is on.
 */
public interface HomeAccess {

    /** True when homes are enabled and the player has the named home. */
    boolean hasHome(UUID uuid, String homeName);

    /**
     * Teleport {@code player} to {@code homeName} on this backend.
     * Returns the home name on success, empty if missing / wrong server / homes off.
     */
    Optional<String> teleportHome(Player player, String homeName);
}

package org.bukkit;

import java.util.UUID;

/** Online or cached offline player handle. */
public interface OfflinePlayer {

    UUID getUniqueId();

    String getName();

    boolean isOnline();

    boolean hasPlayedBefore();

    long getLastPlayed();

    long getFirstPlayed();

    default boolean isBanned() {
        return false;
    }

    default boolean isWhitelisted() {
        return true;
    }
}

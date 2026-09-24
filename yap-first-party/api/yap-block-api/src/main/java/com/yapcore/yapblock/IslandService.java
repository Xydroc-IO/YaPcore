package com.yapcore.yapblock;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Public skyblock island contract for soft-depend plugins. */
public interface IslandService {

    boolean enabled();

    Optional<IslandSnapshot> islandById(long islandId);

    Optional<IslandSnapshot> islandAt(Location location);

    Optional<IslandSnapshot> islandOf(UUID playerId);

    Optional<IslandRole> role(UUID playerId, long islandId);

    boolean canBuild(Player player, Location location);

    boolean canEnter(Player player, Location location);

    CompletableFuture<Optional<IslandSnapshot>> createIsland(Player player);

    CompletableFuture<Boolean> teleportHome(Player player);

    /**
     * Fleet portal / join landing: teleport to the player's island home, creating one
     * on first arrival when they have none.
     */
    CompletableFuture<Boolean> arriveOrCreate(Player player);

    List<IslandSnapshot> topIslands(int limit);
}

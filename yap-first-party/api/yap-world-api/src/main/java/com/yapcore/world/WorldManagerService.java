package com.yapcore.world;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Multiverse-class world management contract.
 */
public interface WorldManagerService {

    Collection<String> loadedWorlds();

    CompletableFuture<Boolean> loadWorld(String name);

    CompletableFuture<Boolean> unloadWorld(String name);

    CompletableFuture<Boolean> teleportToWorldSpawn(UUID playerUuid, String worldName);
}

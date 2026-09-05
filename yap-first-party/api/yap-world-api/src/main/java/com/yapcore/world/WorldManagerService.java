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

    /**
     * Load an existing world folder, or create it with default settings if missing.
     */
    CompletableFuture<Boolean> loadWorld(String name);

    /**
     * Create (or load-if-exists) a world with explicit type / environment / seed / generator.
     * Type and seed apply only when the world is generated for the first time.
     */
    CompletableFuture<Boolean> createWorld(String name, WorldCreateOptions options);

    CompletableFuture<Boolean> unloadWorld(String name);

    /**
     * Unload and permanently delete a world folder (ephemeral instances).
     * Only sanitized folder names under the server root are accepted.
     */
    CompletableFuture<Boolean> deleteWorld(String name);

    CompletableFuture<Boolean> teleportToWorldSpawn(UUID playerUuid, String worldName);
}

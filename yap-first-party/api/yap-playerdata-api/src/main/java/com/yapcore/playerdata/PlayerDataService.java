package com.yapcore.playerdata;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Session lock + profile network state for Link suite and moderation alt linking.
 * Provided by {@code YaPPlayerData} via {@code ServicesManager}.
 */
public interface PlayerDataService {

    String serverId();

    Optional<String> lockHolder(UUID uuid);

    CompletableFuture<Boolean> tryAcquireSessionLock(UUID uuid, String serverId);

    CompletableFuture<Void> releaseSessionLock(UUID uuid, String serverId);

    CompletableFuture<Optional<String>> lastKnownIp(UUID uuid);
}

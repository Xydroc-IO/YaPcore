package com.yapcore.moderation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Punishment and moderation history service.
 * Provided by {@code YaPModeration} via {@code ServicesManager}.
 */
public interface ModerationService {

    boolean isBanned(UUID uuid);

    boolean isIpBanned(String address);

    boolean isMuted(UUID uuid);

    Optional<Punishment> activeBan(UUID uuid);

    Optional<Punishment> activeMute(UUID uuid);

    Optional<Punishment> activeIpBan(String address);

    CompletableFuture<List<Punishment>> history(UUID uuid, int limit);

    CompletableFuture<Punishment> ban(UUID target, String targetName, UUID actor, String actorName,
                                      String reason, long expiresAtEpochMs, boolean ipBan);

    CompletableFuture<Punishment> mute(UUID target, String targetName, UUID actor, String actorName,
                                       String reason, long expiresAtEpochMs);

    CompletableFuture<Boolean> unban(UUID target, UUID actor, String actorName, String reason);

    CompletableFuture<Boolean> unmute(UUID target, UUID actor, String actorName, String reason);

    CompletableFuture<Punishment> warn(UUID target, String targetName, UUID actor, String actorName, String reason);

    void reload();
}

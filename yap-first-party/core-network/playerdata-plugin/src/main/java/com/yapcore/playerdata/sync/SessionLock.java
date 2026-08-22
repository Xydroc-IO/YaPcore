package com.yapcore.playerdata.sync;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.PlayerRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Dual-login / cross-server session guard via {@code lock_server} / {@code lock_until}.
 */
public final class SessionLock {

    public record LockInfo(boolean heldByUs, String holderServer, Instant until) {
    }

    private final PlayerRepository repository;
    private final PlayerDataConfig config;

    public SessionLock(PlayerRepository repository, PlayerDataConfig config) {
        this.repository = repository;
        this.config = config;
    }

    public boolean tryAcquire(UUID uuid) throws SQLException {
        Instant now = Instant.now();
        Instant until = now.plusSeconds(config.lockTtlSeconds());
        return repository.acquireLock(uuid, config.serverId(), now, until);
    }

    /** Attempt acquire; if failed, return who holds the lock. */
    public Optional<String> tryAcquireOrHolder(UUID uuid) throws SQLException {
        if (tryAcquire(uuid)) {
            return Optional.empty();
        }
        return repository.find(uuid).map(r -> {
            String holder = r.lockServer();
            if (holder == null || holder.isBlank()) {
                return "unknown";
            }
            return holder;
        });
    }

    public void refresh(UUID uuid) throws SQLException {
        Instant until = Instant.now().plusSeconds(config.lockTtlSeconds());
        repository.refreshLock(uuid, config.serverId(), until);
    }

    public void release(UUID uuid) throws SQLException {
        repository.releaseLock(uuid, config.serverId());
    }

    /** Force-clear any lock for this uuid (admin / stuck recovery). */
    public void forceRelease(UUID uuid) throws SQLException {
        repository.forceReleaseLock(uuid);
    }
}

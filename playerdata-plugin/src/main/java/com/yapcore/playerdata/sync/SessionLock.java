package com.yapcore.playerdata.sync;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.db.PlayerRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * Dual-login guard via {@code lock_server} / {@code lock_until}.
 */
public final class SessionLock {

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

    public void refresh(UUID uuid) throws SQLException {
        Instant until = Instant.now().plusSeconds(config.lockTtlSeconds());
        repository.refreshLock(uuid, config.serverId(), until);
    }

    public void release(UUID uuid) throws SQLException {
        repository.releaseLock(uuid, config.serverId());
    }
}

package com.yapcore.playerdata.economy;

import com.yapcore.playerdata.db.PlayerRecord;
import com.yapcore.playerdata.db.PlayerRepository;
import com.yapcore.playerdata.sync.SyncService;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Balance reads/writes for online cache + offline DB.
 */
public final class BalanceStore {

    private final SyncService sync;
    private final PlayerRepository repository;
    private final Logger logger;

    public BalanceStore(SyncService sync, PlayerRepository repository, Logger logger) {
        this.sync = sync;
        this.repository = repository;
        this.logger = logger;
    }

    public double getBalance(UUID uuid) {
        if (sync.isReady(uuid)) {
            return sync.getBalance(uuid);
        }
        try {
            return repository.find(uuid).map(PlayerRecord::balance).orElse(0.0);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Balance read failed for " + uuid, e);
            return 0.0;
        }
    }

    public void setBalance(UUID uuid, double amount) {
        double rounded = Math.round(amount * 100.0) / 100.0;
        if (sync.isReady(uuid)) {
            sync.saveBalanceAsync(uuid, rounded);
            return;
        }
        try {
            repository.ensure(uuid, "unknown");
            repository.saveBalance(uuid, rounded);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Balance write failed for " + uuid, e);
        }
    }

    public boolean transfer(UUID from, UUID to, double amount) {
        if (amount <= 0) {
            return false;
        }
        double fromBal = getBalance(from);
        if (fromBal < amount) {
            return false;
        }
        setBalance(from, fromBal - amount);
        setBalance(to, getBalance(to) + amount);
        return true;
    }
}

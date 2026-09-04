package com.yapcore.playerdata.service;

import com.yapcore.playerdata.PlayerDataConfig;
import com.yapcore.playerdata.PlayerDataService;
import com.yapcore.playerdata.db.AuthRepository;
import com.yapcore.playerdata.db.PlayerRepository;
import com.yapcore.playerdata.economy.BalanceStore;
import com.yapcore.playerdata.sync.PlaytimeTracker;
import com.yapcore.playerdata.sync.SessionLock;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerDataServiceImpl implements PlayerDataService {

    private final JavaPlugin plugin;
    private final PlayerDataConfig config;
    private final SessionLock locks;
    private final PlayerRepository repository;
    private final AuthRepository authRepository;
    private final BalanceStore balances;
    private PlaytimeTracker playtime;

    public PlayerDataServiceImpl(JavaPlugin plugin, PlayerDataConfig config, SessionLock locks,
                                 PlayerRepository repository, AuthRepository authRepository,
                                 BalanceStore balances) {
        this.plugin = plugin;
        this.config = config;
        this.locks = locks;
        this.repository = repository;
        this.authRepository = authRepository;
        this.balances = balances;
    }

    public void bindPlaytime(PlaytimeTracker playtime) {
        this.playtime = playtime;
    }

    @Override
    public String serverId() {
        return config.serverId();
    }

    @Override
    public Optional<String> lockHolder(UUID uuid) {
        try {
            return repository.find(uuid)
                    .flatMap(record -> {
                        String holder = record.lockServer();
                        Timestamp until = record.lockUntil();
                        if (holder == null || holder.isBlank() || until == null) {
                            return Optional.empty();
                        }
                        if (until.toInstant().isBefore(Instant.now())) {
                            return Optional.empty();
                        }
                        return Optional.of(holder);
                    });
        } catch (SQLException e) {
            plugin.getLogger().warning("lockHolder lookup failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public CompletableFuture<Boolean> tryAcquireSessionLock(UUID uuid, String serverId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!config.serverId().equals(serverId)) {
                return false;
            }
            try {
                return locks.tryAcquire(uuid);
            } catch (SQLException e) {
                plugin.getLogger().warning("tryAcquireSessionLock failed: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> releaseSessionLock(UUID uuid, String serverId) {
        return CompletableFuture.runAsync(() -> {
            if (!config.serverId().equals(serverId)) {
                return;
            }
            try {
                locks.release(uuid);
            } catch (SQLException e) {
                plugin.getLogger().warning("releaseSessionLock failed: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Optional<String>> lastKnownIp(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return authRepository.findByUuid(uuid)
                        .map(AuthRepository.Account::lastIp)
                        .filter(ip -> ip != null && !ip.isBlank());
            } catch (SQLException e) {
                plugin.getLogger().warning("lastKnownIp failed: " + e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    public boolean economyEnabled() {
        return config.economyEnabled();
    }

    @Override
    public double balance(UUID uuid) {
        if (!config.economyEnabled() || uuid == null) {
            return 0.0;
        }
        return balances.getBalance(uuid);
    }

    @Override
    public Optional<Double> deposit(UUID uuid, double amount) {
        if (!config.economyEnabled() || uuid == null || amount < 0
                || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return Optional.empty();
        }
        double before = balances.getBalance(uuid);
        double next = before + amount;
        balances.setBalance(uuid, next);
        double after = balances.getBalance(uuid);
        balances.fireBalanceChange(uuid, before, after);
        return Optional.of(after);
    }

    @Override
    public Optional<Double> withdraw(UUID uuid, double amount) {
        if (!config.economyEnabled() || uuid == null || amount < 0
                || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return Optional.empty();
        }
        double current = balances.getBalance(uuid);
        if (current < amount) {
            return Optional.empty();
        }
        balances.setBalance(uuid, current - amount);
        double after = balances.getBalance(uuid);
        balances.fireBalanceChange(uuid, current, after);
        return Optional.of(after);
    }

    @Override
    public Optional<Double> setBalance(UUID uuid, double amount) {
        if (!config.economyEnabled() || uuid == null || amount < 0
                || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return Optional.empty();
        }
        double before = balances.getBalance(uuid);
        balances.setBalance(uuid, amount);
        double after = balances.getBalance(uuid);
        balances.fireBalanceChange(uuid, before, after);
        return Optional.of(after);
    }

    @Override
    public long playMinutes(UUID uuid) {
        if (playtime == null || uuid == null) {
            return 0L;
        }
        return playtime.playMinutes(uuid);
    }
}
